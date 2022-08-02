package org.rri.server.symbol;

import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.psi.*;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyFunctionImpl;
import org.eclipse.lsp4j.SymbolKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.server.util.MiscUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class TypeUtil {
  private static final Logger LOG = Logger.getInstance(MiscUtil.class);

  @SuppressWarnings("UnstableApiUsage")
  @Nullable
  public static SymbolKind symbolKind(@NotNull PsiElement elem) {
    if (elem instanceof PyElement) {
      return pySymbolKind((PyElement) elem);
    } else if (elem instanceof PsiFile) {
      return SymbolKind.File;
    } else if (elem instanceof PsiPackageStatement) {
      return SymbolKind.Package;
    } else if (elem instanceof PsiImportStatement) {
      return SymbolKind.Module;
    } else if (elem instanceof PsiClass) {
      final var elemClass = (PsiClass) elem;
      if (elemClass.isAnnotationType() || elemClass.isInterface()) {
        return SymbolKind.Interface;
      } else if (elemClass.isEnum()) {
        return SymbolKind.Enum;
      } else {
        return SymbolKind.Class;
      }
    } else if (elem instanceof PsiClassInitializer) {
      return SymbolKind.Constructor;
    } else if (elem instanceof PsiMethod) {
      return ((PsiMethod) elem).isConstructor() ? SymbolKind.Constructor : SymbolKind.Method;
    } else if (elem instanceof PsiEnumConstant) {
      return SymbolKind.EnumMember;
    } else if (elem instanceof PsiField) {
      final var elemField = (PsiField) elem;
      return elemField.hasModifier(JvmModifier.STATIC) && elemField.hasModifier(JvmModifier.FINAL)
          ? SymbolKind.Constant : SymbolKind.Field;
    } else if (elem instanceof PsiVariable) {
      return SymbolKind.Variable;
    } else if (elem instanceof PsiAnnotation) {
      return SymbolKind.Property;
    } else if (elem instanceof PsiLiteralExpression) {
      final var elemLiteral = (PsiLiteralExpression) elem;
      final var type = elemLiteral.getType();
      if (type == null) {
        return SymbolKind.Null;
      } else if (type instanceof PsiClassType && ((PsiClassType) type).getName().equals("String")) {
        return SymbolKind.String;
      } else if (type.equals(PsiType.BOOLEAN)) {
        return SymbolKind.Boolean;
      } else if (type.equals(PsiType.BYTE) || type.equals(PsiType.DOUBLE)
          || type.equals(PsiType.FLOAT) || type.equals(PsiType.INT)
          || type.equals(PsiType.LONG) || type.equals(PsiType.SHORT)) {
        return SymbolKind.Number;
      } else if (type.equals(PsiType.CHAR)) {
        return SymbolKind.String;
      } else if (type.equals(PsiType.NULL) || type.equals(PsiType.VOID)) {
        return SymbolKind.Null;
      } else {
        return SymbolKind.Constant;
      }
    }
    return null;
  }

  @Nullable
  public static SymbolKind pySymbolKind(@NotNull PyElement elem) {
    if (elem instanceof PyFile) {
      return SymbolKind.File;
    } else if (elem instanceof PyNoneLiteralExpression) {
      return SymbolKind.Null;
    } else if (elem instanceof PyNumericLiteralExpression) {
      return SymbolKind.Number;
    } else if (elem instanceof PyStringLiteralExpression) {
      return SymbolKind.String;
    } else if (elem instanceof PyBoolLiteralExpression) {
      return SymbolKind.Boolean;
    } else if (elem instanceof PyParameter) {
      return ((PyParameter) elem).isSelf() ? SymbolKind.Field : SymbolKind.Variable;
    } else if (elem instanceof PyDecorator) {
      return SymbolKind.Property;
    } else if (elem instanceof PyImportStatementBase) {
      return SymbolKind.Module;
    } else if (elem instanceof PyClass) {
      return isPyEnum((PyClass) elem) ? SymbolKind.Enum : SymbolKind.Class;
    } else if (elem instanceof PyFunction) {
      if (elem instanceof PyFunctionImpl && ((PyFunctionImpl) elem).asMethod() == null) {
        return SymbolKind.Function;
      }
      return Objects.equals(elem.getName(), "__init__") ? SymbolKind.Constructor : SymbolKind.Method;
    } else if (elem instanceof PyTargetExpression) {
      final var targetElem = (PyTargetExpression) elem;
      final var name = targetElem.asQualifiedName();
      if (name != null && name.getLastComponent() != null) {
        final var lstComp = name.getLastComponent();
        if (name.join(".").contains("self")) {
          if (isPyEnum(targetElem.getContainingClass())) {
            return SymbolKind.EnumMember;
          }
          return lstComp.startsWith("_") ? SymbolKind.Constant : SymbolKind.Field;
        }
        return lstComp.startsWith("_") ? SymbolKind.Constant : SymbolKind.Variable;
      }
    }
    return null;
  }

  private static boolean isPyEnum(@Nullable PyClass clazz) {
    if (clazz == null) {
      return false;
    }
    final var superClasses = clazz.getSuperClasses(null);
    return Arrays.stream(superClasses)
        .filter(pyClass -> pyClass.getQualifiedName() != null
            && pyClass.getQualifiedName().contains("Enum"))
        .findFirst().orElse(null) != null;
  }

  @Nullable
  public static String symbolName(PsiElement elem) {
    if (elem instanceof PyElement) {
      return pySymbolName((PyElement) elem);
    } else if (elem instanceof PsiFile) {
      return ((PsiFile) elem).getName();
    } else if (elem instanceof PsiPackageStatement) {
      return ((PsiPackageStatement) elem).getPackageName();
    } else if (elem instanceof PsiImportStatement) {
      final var qualifiedName = ((PsiImportStatement) elem).getQualifiedName();
      return qualifiedName == null ? "<error>" : qualifiedName;
    } else if (elem instanceof PsiClass) {
      final var elemClass = (PsiClass) elem;
      if (elemClass.getName() != null) {
        return elemClass.getName();
      }
      return elemClass.getQualifiedName() == null ? "<anonymous>" : elemClass.getQualifiedName();
    } else if (elem instanceof PsiClassInitializer) {
      String name = ((PsiClassInitializer) elem).getName();
      return name == null ? "<init>" : name;
    } else if (elem instanceof PsiMethod) {
      return methodLabel((PsiMethod) elem);
    } else if (elem instanceof PsiEnumConstant) {
      return ((PsiEnumConstant) elem).getName();
    } else if (elem instanceof PsiField) {
      return ((PsiField) elem).getName();
    } else if (elem instanceof PsiVariable) {
      String name = ((PsiVariable) elem).getName();
      return name == null ? "<unknown>" : name;
    } else if (elem instanceof PsiAnnotation) {
      return annotationLabel((PsiAnnotation) elem);
    } else if (elem instanceof PsiLiteralExpression) {
      return elem.getText();
    } else {
      return null;
    }
  }

  @NotNull
  private static String methodLabel(@NotNull PsiMethod method) {
    return method.getName() + Arrays.stream(method.getParameterList().getParameters())
        .map(TypeUtil::methodParameterLabel)
        .collect(Collectors.joining(", ", "(", ")"));
  }

  @NotNull
  private static String methodParameterLabel(@NotNull PsiParameter parameter) {
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
      final var wt = (PsiWildcardType) typeToGen;
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
          if (i < params.length - 1) {
            subst.append(", ");
          }
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

  @Nullable
  private static String pySymbolName(PyElement elem) {
    if (elem instanceof PyFile
        || elem instanceof PyLiteralExpression
        || elem instanceof PyParameter
        || elem instanceof PyReferenceExpression
        || elem instanceof PyTargetExpression) {
      return elem.getName();
    } else if (elem instanceof PyDecorator) {
      return pyDecoratorLabel((PyDecorator) elem);
    } else if (elem instanceof PyImportStatementBase) {
      final var qName = ((PyImportStatementBase) elem).getFullyQualifiedObjectNames();
      return qName.isEmpty() ? "<error>" : String.join(".", qName);
    } else if (elem instanceof PyClass) {
      final var elemClass = (PyClass) elem;
      return elemClass.getName() != null ? elemClass.getName() : elemClass.getQualifiedName();
    } else if (elem instanceof PyFunction) {
      return pyFunctionLabel((PyFunction) elem);
    }
    return null;
  }

  @NotNull
  private static String pyFunctionLabel(@NotNull PyFunction function) {
    return function.getName() + (function.getParameterList().getParameters().length > 0 ? "(*args, **kwargs)" : "()");
  }

  @NotNull
  private static String pyDecoratorLabel(@NotNull PyDecorator decorator) {
    final var name = decorator.getName() == null
        ? decorator.getQualifiedName() : decorator.getName();
    return name == null ? "<unknown>" : "@" + name;
  }
}
