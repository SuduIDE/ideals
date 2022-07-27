package org.rri.server.util;

import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.server.LspPath;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MiscUtil {
  private static final Logger LOG = Logger.getInstance(MiscUtil.class);

  private MiscUtil() { }

  @NotNull
  public static <T> T with(@NotNull T object, @NotNull Consumer<T> block) {
    block.accept(object);
    return object;
  }

  @NotNull
  public static Position offsetToPosition(@NotNull Document doc, int offset) {
    if (offset == -1) {
      return new Position(0, 0);
    }
    var line = doc.getLineNumber(offset);
    var lineStartOffset = doc.getLineStartOffset(line);
    var column = offset - lineStartOffset;
    return new Position(line, column);
  }

  @Nullable
  public static PsiFile resolvePsiFile(@NotNull Project project, @NotNull LspPath path) {
    var result = new Ref<PsiFile>();
    withPsiFileInReadAction(project, path, result::set);
    return result.get();
  }

  public static void withPsiFileInReadAction(@NotNull Project project, @NotNull LspPath path, @NotNull Consumer<@NotNull PsiFile> block) {
    final var virtualFile = path.findVirtualFile();

    if (virtualFile == null) {
      LOG.info("File not found: " + path);
      return;
    }

    ApplicationManager.getApplication().runReadAction(() -> {
      final var psiFile = PsiManager.getInstance(project).findFile(virtualFile);

      if (psiFile == null) {
        LOG.info("Unable to get PSI for virtual file: " + virtualFile);
        return;
      }

      block.accept(psiFile);
    });
  }

  @Nullable
  public static Document getDocument(@NotNull PsiFile file) {
    var virtualFile = file.getVirtualFile();

    if (virtualFile == null)
      return file.getViewProvider().getDocument();

    var doc = FileDocumentManager.getInstance().getDocument(virtualFile);

    if (doc == null) {
      FileDocumentManagerImpl.registerDocument(
              new DocumentImpl(file.getViewProvider().getContents()),
              virtualFile);
      doc = FileDocumentManager.getInstance()
              .getDocument(virtualFile);
    }

    return doc;
  }

  @NotNull
  public static Runnable asWriteAction(@NotNull Runnable action) {
    return () -> ApplicationManager.getApplication().runWriteAction(action);
  }

  @NotNull
  public static RuntimeException wrap(@NotNull Exception e) {
    return e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
  }

  public interface RunnableWithException {
    void run() throws Exception;
  }

  public static Runnable asRunnable(@NotNull MiscUtil.RunnableWithException action) {
    return () -> {
      try {
        action.run();
      } catch (Exception e) {
        throw wrap(e);
      }
    };
  }

  public static <T> T makeThrowsUnchecked(@NotNull Callable<T> block) {
    try {
      return block.call();
    } catch (Exception e) {
      throw wrap(e);
    }
  }

  @Nullable
  public static LocationLink psiElementToLocationLink(@NotNull PsiElement targetElem, @Nullable Document doc, @Nullable Range originalRange) {
    if (doc == null) {
      return null;
    }
    Range range = getPsiElementRange(doc, targetElem);
    String uri = LspPath.fromVirtualFile(targetElem.getContainingFile().getVirtualFile()).toLspUri();
    return range != null ? new LocationLink(uri, range, range, originalRange) : null;
  }

  @Nullable
  public static Location psiElementToLocation(@Nullable PsiElement elem) {
    if (elem == null) {
      return null;
    }
    var file = elem.getContainingFile();
    var doc = getDocument(file);
    if (doc == null) {
      return null;
    }
    var uri = LspPath.fromVirtualFile(file.getVirtualFile()).toLspUri();
    Range range = getPsiElementRange(doc, elem);
    return range != null ? new Location(uri, range) : null;
  }

  @Nullable
  public static Range getPsiElementRange(@NotNull Document doc, @Nullable PsiElement elem) {
    TextRange range = null;
    if (elem == null) {
      return null;
    }
    if (elem instanceof PsiNameIdentifierOwner) {
      PsiElement identifier = ((PsiNameIdentifierOwner) elem).getNameIdentifier();
      if (identifier != null) {
        range = identifier.getTextRange();
      }
    }
    if (range == null) {
      range = elem.getTextRange();
    }
    return range != null ? new Range(offsetToPosition(doc, range.getStartOffset()), offsetToPosition(doc, range.getEndOffset())) : null;
  }

  public static int positionToOffset(@NotNull Document doc, @NotNull Position pos) {
    return doc.getLineStartOffset(pos.getLine()) + pos.getCharacter();
  }


  @SuppressWarnings("UnstableApiUsage")
  @Nullable
  public static SymbolKind symbolKind(@NotNull PsiElement elem) {
    if (elem instanceof PsiFile) { return SymbolKind.File; }
    else if (elem instanceof PsiPackageStatement) { return SymbolKind.Package; }
    else if (elem instanceof PsiImportStatement) { return SymbolKind.Module; }
    else if (elem instanceof PsiClass) {
      final var elemClass = (PsiClass) elem;
      if (elemClass.isAnnotationType() || elemClass.isInterface()) { return SymbolKind.Interface; }
      else if (elemClass.isEnum()) { return SymbolKind.Enum; }
      else { return SymbolKind.Class; }
    } else if (elem instanceof PsiClassInitializer) { return SymbolKind.Constructor; }
    else if (elem instanceof PsiMethod) { return ((PsiMethod) elem).isConstructor() ? SymbolKind.Constructor : SymbolKind.Method; }
    else if (elem instanceof PsiEnumConstant) { return SymbolKind.EnumMember; }
    else if (elem instanceof PsiField) {
      final var elemField = (PsiField) elem;
      return elemField.hasModifier(JvmModifier.STATIC) && elemField.hasModifier(JvmModifier.FINAL)
              ? SymbolKind.Constant : SymbolKind.Field;
    } else if (elem instanceof PsiVariable) { return SymbolKind.Variable; }
    else if (elem instanceof PsiAnnotation) { return SymbolKind.Property; }
    else if(elem instanceof PsiLiteralExpression) {
      final var elemLiteral = (PsiLiteralExpression) elem;
      final var type = elemLiteral.getType();
      if (type == null) { return SymbolKind.Null; }
      else if (type instanceof PsiClassType && ((PsiClassType) type).getName().equals("String")) { return SymbolKind.String; }
      else if (type.equals(PsiType.BOOLEAN)) { return SymbolKind.Boolean; }
      else if (type.equals(PsiType.BYTE) || type.equals(PsiType.DOUBLE)
              || type.equals(PsiType.FLOAT) || type.equals(PsiType.INT)
              || type.equals(PsiType.LONG) || type.equals(PsiType.SHORT)) { return SymbolKind.Number; }
      else if (type.equals(PsiType.CHAR)) { return  SymbolKind.String; }
      else if (type.equals(PsiType.NULL) || type.equals(PsiType.VOID)) { return SymbolKind.Null; }
      else { return SymbolKind.Constant; }
    } else { return null; }
  }

  @Nullable
  public static String symbolName(PsiElement elem) {
    if (elem instanceof PsiFile) { return ((PsiFile) elem).getName(); }
    else if (elem instanceof PsiPackageStatement) { return ((PsiPackageStatement) elem).getPackageName(); }
    else if (elem instanceof PsiImportStatement) {
      final var qualifiedName = ((PsiImportStatement) elem).getQualifiedName();
      return qualifiedName == null ? "<error>" : qualifiedName;
    } else if (elem instanceof PsiClass) {
      final var elemClass = (PsiClass) elem;
      if (elemClass.getName() != null) { return elemClass.getName(); }
      return elemClass.getQualifiedName() == null ? "<anonymous>" : elemClass.getQualifiedName();
    } else if (elem instanceof PsiClassInitializer) {
      String name = ((PsiClassInitializer) elem).getName();
      return name == null ? "<init>" : name;
    } else if (elem instanceof PsiMethod) { return methodLabel((PsiMethod) elem); }
    else if (elem instanceof PsiEnumConstant) { return ((PsiEnumConstant) elem).getName(); }
    else if (elem instanceof PsiField) { return ((PsiField) elem).getName(); }
    else if (elem instanceof PsiVariable) {
      String name = ((PsiVariable) elem).getName();
      return name == null ? "<unknown>" : name;
    } else if (elem instanceof PsiAnnotation) { return annotationLabel((PsiAnnotation) elem); }
    else if (elem instanceof PsiLiteralExpression) { return elem.getText(); }
    else { return null; }
  }

  @NotNull
  private static String methodLabel(@NotNull PsiMethod method) {
    return method.getName() + Arrays.stream(method.getParameterList().getParameters())
            .map(param -> methodParameterLabel(method, param))
            .collect(Collectors.joining(", ", "(", ")"));
  }

  @NotNull
  private static String methodParameterLabel(@NotNull PsiMethod method, @NotNull PsiParameter parameter) {
    final var buffer = new StringBuilder();
    generateType(buffer, parameter.getType(), false, true);
    return buffer.toString();
  }

  private static String annotationLabel(PsiAnnotation annotation) {
    final var name = annotation.getNameReferenceElement() == null
            ? annotation.getQualifiedName() : annotation.getNameReferenceElement().getText();
    return name == null ? "<unknown>" : "@" + name;
  }

  private static int generateType(@NotNull StringBuilder buffer,
                                  @Nullable PsiType type,
                                  boolean useShortNames,
                                  boolean generateLink) {
    var typeToGen = type;
    if (typeToGen instanceof PsiPrimitiveType) {
      final var primitiveType = (PsiPrimitiveType) typeToGen;
      final var text = useShortNames ? primitiveType.getPresentableText() : primitiveType.getCanonicalText();
      buffer.append(text);
      return text.length();
    }

    if (typeToGen instanceof PsiArrayType) {
      final var rest = generateType(buffer, ((PsiArrayType) typeToGen).getComponentType(), generateLink, useShortNames);
      if (typeToGen instanceof PsiEllipsisType) {
        buffer.append("...");
        return rest + 3;
      } else {
        buffer.append("[]");
        return rest + 2;
      }
    }

    if (typeToGen instanceof PsiCapturedWildcardType) {
      typeToGen = ((PsiCapturedWildcardType) typeToGen).getWildcard();
    }

    if (typeToGen instanceof PsiWildcardType) {
      final var wt =  (PsiWildcardType) typeToGen;
      buffer.append("?");
      final var bound = wt.getBound();
      if (bound != null) {
        final var keyword = wt.isExtends() ? " extends " : " super ";
        buffer.append(keyword);
        return generateType(buffer, bound, generateLink, useShortNames) + 1 + keyword.length();
      } else {
        return 1;
      }
    }

    if (typeToGen instanceof PsiClassType) {
      PsiClassType.ClassResolveResult result;
      final var classTypeToGen = (PsiClassType) typeToGen;
      try {
        result = classTypeToGen.resolveGenerics();
      } catch (IndexNotReadyException e) {
        LOG.debug(e);
        final var text = classTypeToGen.getClassName();
        buffer.append(text);
        return text.length();
      }

      final var psiClass = result.getElement();
      final var psiSubst = result.getSubstitutor();

      if (psiClass == null) {
        final var text = useShortNames ? typeToGen.getPresentableText() : typeToGen.getCanonicalText();
        buffer.append(text);
        return text.length();
      }

      final var qName = useShortNames ? psiClass.getQualifiedName() : psiClass.getName();
      if (qName == null || psiClass instanceof PsiTypeParameter) {
        final var text = useShortNames ? typeToGen.getPresentableText() : typeToGen.getCanonicalText();
        buffer.append(text);
        return text.length();
      }
      final var name = useShortNames ? classTypeToGen.rawType().getPresentableText() : qName;
      buffer.append(name);
      var length = buffer.length();
      if (psiClass.hasTypeParameters()) {
        final var subst = new StringBuilder();
        final var params = psiClass.getTypeParameters();
        subst.append("<");
        length += 1;
        var goodSubst = true;
        for (int i = 0; i < params.length; ++i) {
          final var t = psiSubst.substitute(params[i]);
          if (t == null) {
            goodSubst = false;
            break;
          }
          length += generateType(subst, t, generateLink, useShortNames);
          if (i < params.length - 1) { subst.append(", "); }
        }
        subst.append(">");
        length += 1;
        if (goodSubst) {
          final var text = subst.toString();
          buffer.append(text);
        }
      }
      return length;
    }

    if (typeToGen instanceof PsiDisjunctionType || typeToGen instanceof PsiIntersectionType) {
      if (!generateLink) {
        final var canonicalText = useShortNames ? typeToGen.getPresentableText() : typeToGen.getCanonicalText();
        int result = canonicalText.length();
        buffer.append(canonicalText);
        return result;
      } else {
        final var separator = typeToGen instanceof PsiDisjunctionType ? " | " : " & ";
        List<PsiType> componentTypes;
        if (typeToGen instanceof PsiIntersectionType) {
          componentTypes = Arrays.asList(((PsiIntersectionType) typeToGen).getConjuncts());
        } else {
          componentTypes = ((PsiDisjunctionType) typeToGen).getDisjunctions();
        }
        var length = 0;
        for (var psiType : componentTypes) {
          if (length > 0) {
            buffer.append(separator);
            length += 3;
          }
          length += generateType(buffer, psiType, useShortNames, false);
        }
        return length;
      }
    }
    return 0;
  }
}
