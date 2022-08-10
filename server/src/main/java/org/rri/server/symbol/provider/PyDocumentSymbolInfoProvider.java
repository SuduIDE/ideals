package org.rri.server.symbol.provider;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyFunctionImpl;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.eclipse.lsp4j.SymbolKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.rri.server.symbol.provider.DocumentSymbolInfoProvider.Info.composeInfo;

public class PyDocumentSymbolInfoProvider implements DocumentSymbolInfoProvider {
  public @Nullable Info calculateSymbolInfo(@NotNull PsiElement psiElement) {
    if (!(psiElement instanceof final PyElement elem)) {
      return null;
    }
    if (elem instanceof PyNamedParameter || elem instanceof PyTupleParameter) {
      final var elemParam = (PyParameter) elem;
      return composeInfo(elemParam.isSelf() || Objects.equals(elem.getName(), "_")
              || elem.getParent().getParent() instanceof PyLambdaExpression
              ? null : SymbolKind.Variable,
          () -> pyFunctionParameterLabel(elemParam));
    } else if (elem instanceof final PyClass elemClass) {
      return composeInfo(isPyEnum((PyClass) elem) ? SymbolKind.Enum : SymbolKind.Class,
          () -> elemClass.getName() == null ? elemClass.getQualifiedName() : elemClass.getName());
    } else if (elem instanceof PyFunction) {
      return composeInfo(
          elem instanceof PyFunctionImpl && ((PyFunctionImpl) elem).asMethod() == null ? SymbolKind.Function
              : Objects.equals(elem.getName(), "__init__") ? SymbolKind.Constructor
              : SymbolKind.Method,
          () -> pyFunctionLabel((PyFunction) elem));
    } else if (elem instanceof final PyTargetExpression targetElem) {
      return composeInfo(pyTargetExprType(targetElem), elem::getName);
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
          String name;
          return (name = pyClass.getQualifiedName()) != null
              && (name.equals("Enum") || name.equals("enum.Enum"));
        })
        .findFirst().orElse(null) != null;
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
      var prefix = namedParameter.isPositionalContainer() ? "*"
          : namedParameter.isKeywordContainer() ? "**" : "";
      final var type = ((PyNamedParameter) param).getArgumentType(TypeEvalContext.codeAnalysis(param.getProject(), param.getContainingFile()));
      return type != null
          && namedParameter.getName() != null
          && !namedParameter.getName().equals("self") ?
          prefix + param.getName() + ": " + type.getName() : prefix + param.getName();
    }
    return param instanceof PySingleStarParameter ? "*"
        : param instanceof PySlashParameter ? "/"
        : param instanceof PyTupleParameter ?
        Arrays.stream(((PyTupleParameter) param).getContents())
            .map(PyDocumentSymbolInfoProvider::pyFunctionParameterLabel)
            .collect(Collectors.joining(", ", "(", ")"))
        : null;
  }

  @Nullable
  private static SymbolKind pyTargetExprType(PyTargetExpression elem) {
    final var name = elem.asQualifiedName();
    if (name != null && name.getLastComponent() != null) {
      final var lastComp = name.getLastComponent();
      return lastComp.equals(lastComp.toUpperCase()) ? SymbolKind.Constant
          : lastComp.equals("_") ? null
          : name.getComponents().size() == 1 ? SymbolKind.Variable
          : isPyEnum(elem.getContainingClass()) ? SymbolKind.EnumMember
          : SymbolKind.Field;
    }
    return null;
  }
}
