package org.rri.server.util;

import com.intellij.lang.jvm.JvmModifier;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.psi.*;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyFunctionImpl;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.eclipse.lsp4j.SymbolKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class SymbolUtil {
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
    } else if (elem instanceof final PsiClass elemClass) {
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
    } else if (elem instanceof final PsiField elemField) {
      return elemField.hasModifier(JvmModifier.STATIC) && elemField.hasModifier(JvmModifier.FINAL)
          ? SymbolKind.Constant : SymbolKind.Field;
    } else if (elem instanceof PsiVariable) {
      return SymbolKind.Variable;
    } else if (elem instanceof PsiAnnotation) {
      return SymbolKind.Property;
    } else if (elem instanceof final PsiLiteralExpression elemLiteral) {
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
    } else if (elem instanceof PyNamedParameter || elem instanceof PyTupleParameter) {
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
    } else if (elem instanceof final PyTargetExpression targetElem) {
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
  public static String symbolName(@NotNull PsiElement elem) {
    if (elem instanceof PyElement) {
      return pySymbolName((PyElement) elem);
    } else if (elem instanceof PsiFile) {
      return ((PsiFile) elem).getName();
    } else if (elem instanceof PsiPackageStatement) {
      return ((PsiPackageStatement) elem).getPackageName();
    } else if (elem instanceof PsiImportStatement) {
      final var qualifiedName = ((PsiImportStatement) elem).getQualifiedName();
      return qualifiedName == null ? "<error>" : qualifiedName;
    } else if (elem instanceof final PsiClass elemClass) {
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
        .map(SymbolUtil::methodParameterLabel)
        .collect(Collectors.joining(", ", "(", ")"));
  }

  @NotNull
  private static String methodParameterLabel(@NotNull PsiParameter parameter) {
    final var buffer = new StringBuilder();
    generateType(buffer, parameter.getType(), false, true);
    return buffer.toString();
  }

  @NotNull
  private static String annotationLabel(@NotNull PsiAnnotation annotation) {
    final var name = annotation.getNameReferenceElement() == null
        ? annotation.getQualifiedName() : annotation.getNameReferenceElement().getText();
    return name == null ? "<unknown>" : "@" + name;
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

  @Nullable
  private static String pySymbolName(@NotNull PyElement elem) {
    if (elem instanceof PyFile
        || elem instanceof PyReferenceExpression
        || elem instanceof PyTargetExpression) {
      return elem.getName();
    } else if (elem instanceof PyNoneLiteralExpression) {
      return "None";
    } else if (elem instanceof final PyNumericLiteralExpression number) {
      if (number.isIntegerLiteral()) {
        return number.getBigIntegerValue() == null ? null : number.getBigIntegerValue().toString();
      } else {
        return number.getBigDecimalValue() == null ? null : number.getBigDecimalValue().toPlainString();
      }
    } else if (elem instanceof PyStringLiteralExpression) {
      return "\"" + ((PyStringLiteralExpression) elem).getStringValue() + "\"";
    } else if (elem instanceof PyBoolLiteralExpression) {
      return ((PyBoolLiteralExpression) elem).getValue() ? "True" : "False";
    } else if (elem instanceof PyDecorator) {
      return pyDecoratorLabel((PyDecorator) elem);
    } else if (elem instanceof PyImportStatement) {
      final var qName = ((PyImportStatement) elem).getFullyQualifiedObjectNames();
      return qName.isEmpty() ? "<error>" : String.join(".", qName);
    } else if (elem instanceof PyFromImportStatement) {
      return pyFromImportLabel((PyFromImportStatement) elem);
    } else if (elem instanceof final PyClass elemClass) {
      return elemClass.getName() != null ? elemClass.getName() : elemClass.getQualifiedName();
    } else if (elem instanceof PyParameter) {
      return pyFunctionParameterLabel((PyParameter) elem);
    } else if (elem instanceof PyFunction) {
      return pyFunctionLabel((PyFunction) elem);
    }
    return null;
  }

  @NotNull
  private static String pyFunctionLabel(@NotNull PyFunction function) {
    return function.getName() + Arrays.stream(function.getParameterList().getParameters())
        .map(SymbolUtil::pyFunctionParameterLabel)
        .filter(Objects::nonNull)
        .collect(Collectors.joining(", ", "(", ")"));
  }

  @Nullable
  private static String pyFunctionParameterLabel(@NotNull PyParameter param) {
    if (param instanceof final PyNamedParameter namedParameter) {
      var prefix = namedParameter.isPositionalContainer() ? "*" : "";
      if (namedParameter.isKeywordContainer()) {
        prefix = "**";
      }
      final var type = ((PyNamedParameter) param).getArgumentType(TypeEvalContext.codeAnalysis(param.getProject(), param.getContainingFile()));
      if (type != null && namedParameter.getName() != null && !namedParameter.getName().equals("self")) {
        return prefix + param.getName() + ": " + type.getName();
      }
      return prefix + param.getName();
    } else if (param instanceof PySingleStarParameter) {
      return "*";
    } else if (param instanceof PySlashParameter) {
      return "/";
    } else if (param instanceof PyTupleParameter) {
      return Arrays.stream(((PyTupleParameter) param).getContents())
          .map(SymbolUtil::pyFunctionParameterLabel)
          .collect(Collectors.joining(", ", "(", ")"));
    }
    return null;
  }

  @NotNull
  private static String pyDecoratorLabel(@NotNull PyDecorator decorator) {
    final var name = decorator.getName() == null
        ? decorator.getQualifiedName() : decorator.getName();
    return name == null ? "<unknown>" : "@" + name;
  }

  @Nullable
  private static String pyFromImportLabel(@NotNull PyFromImportStatement statement) {
    if (statement.getImportSource() == null) {
      return null;
    }
    return "from " + statement.getImportSource().getName() + " import "
        + (statement.isStarImport() ? "*" : Arrays.stream(statement.getImportElements())
            .map(NavigationItem::getName)
            .filter(Objects::nonNull)
            .collect(Collectors.joining(", ")));
  }
}
