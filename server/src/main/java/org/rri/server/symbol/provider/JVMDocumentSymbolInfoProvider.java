package org.rri.server.symbol.provider;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public abstract class JVMDocumentSymbolInfoProvider extends DocumentSymbolInfoProviderBase {
  private static final Logger LOG = Logger.getInstance(JVMDocumentSymbolInfoProvider.class);

  @NotNull
  protected static String methodLabel(@NotNull PsiMethod method) {
    return method.getName() + Arrays.stream(method.getParameterList().getParameters())
        .map(JVMDocumentSymbolInfoProvider::methodParameterLabel)
        .collect(Collectors.joining(", ", "(", ")"));
  }

  @NotNull
  private static String methodParameterLabel(@NotNull PsiParameter parameter) {
    final var buffer = new StringBuilder();
    generateType(buffer, parameter.getType(), false, true);
    return buffer.toString();
  }

  private static int generateType(@NotNull StringBuilder buffer,
                                  @Nullable PsiType type,
                                  boolean useShortNames,
                                  boolean generateLink) {
    var typeToGen = type;
    if (typeToGen instanceof final PsiPrimitiveType primitiveType) {
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

    if (typeToGen instanceof final PsiWildcardType wt) {
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

    if (typeToGen instanceof final PsiClassType classTypeToGen) {
      PsiClassType.ClassResolveResult result;
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

  public boolean isDeprecated(@NotNull PsiElement elem) {
    if (elem instanceof PsiDocCommentOwner) {
      return ((PsiDocCommentOwner) elem).isDeprecated();
    }
    return false;
  }
}
