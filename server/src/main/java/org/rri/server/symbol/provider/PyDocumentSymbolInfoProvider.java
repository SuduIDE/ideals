package org.rri.server.symbol.provider;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyFunctionImpl;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.eclipse.lsp4j.SymbolKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class PyDocumentSymbolInfoProvider implements DocumentSymbolInfoProvider {
  private final Set<QualifiedName> fields = new HashSet<>();

  @Override
  public @Nullable SymbolKind symbolKind(@NotNull PsiElement psiElement) {
    if (!(psiElement instanceof final PyElement elem)) {
      return null;
    }
    if (elem instanceof PyNamedParameter || elem instanceof PyTupleParameter) {
      return ((PyParameter) elem).isSelf() || Objects.equals(elem.getName(), "_")
          || elem.getParent().getParent() instanceof PyLambdaExpression
          ? null : SymbolKind.Variable;
    } else if (elem instanceof PyDecorator) {
      return SymbolKind.Property;
    } else if (elem instanceof PyClass) {
      return isPyEnum((PyClass) elem) ? SymbolKind.Enum : SymbolKind.Class;
    } else if (elem instanceof PyFunction) {
      if (elem instanceof PyFunctionImpl && ((PyFunctionImpl) elem).asMethod() == null) {
        return SymbolKind.Function;
      }
      return Objects.equals(elem.getName(), "__init__") ? SymbolKind.Constructor : SymbolKind.Method;
    } else if (elem instanceof final PyTargetExpression targetElem) {
      final var name = targetElem.asQualifiedName();
      if (name != null && !fields.contains(name) && name.getLastComponent() != null) {
        final var lastComp = name.getLastComponent();
        if (lastComp.equals(lastComp.toUpperCase())) {
          return SymbolKind.Constant;
        } else if (lastComp.equals("_")) {
          return null;
        }
        targetElem.getReference().resolve();
        if (name.getComponents().contains("self")) {
          fields.add(name);
          return isPyEnum(targetElem.getContainingClass()) ? SymbolKind.EnumMember : SymbolKind.Field;
        }
        return SymbolKind.Variable;
      }
    }
    return null;
  }

  private static boolean isPyEnum(@Nullable PyClass elem) {
    if (elem == null) {
      return false;
    }
    final var superClasses = elem.getSuperClasses(TypeEvalContext.codeAnalysis(elem.getProject(), elem.getContainingFile()));
    return Arrays.stream(superClasses)
        .filter(pyClass -> {
          String name = pyClass.getQualifiedName();
          if (name == null) { return false; }
          return name.equals("Enum") || name.equals("enum.Enum");
        })
        .findFirst().orElse(null) != null;
  }

  @Override
  public @Nullable String symbolName(@NotNull PsiElement psiElement) {
    if (!(psiElement instanceof final PyElement elem)) {
      return null;
    }
    if (elem instanceof PyReferenceExpression
        || elem instanceof PyTargetExpression) {
      return elem.getName();
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
        .map(PyDocumentSymbolInfoProvider::pyFunctionParameterLabel)
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
          .map(PyDocumentSymbolInfoProvider::pyFunctionParameterLabel)
          .collect(Collectors.joining(", ", "(", ")"));
    }
    return null;
  }
}
