package org.rri.server.symbol.provider;

import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import org.eclipse.lsp4j.SymbolKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaDocumentSymbolInfoProvider extends JVMDocumentSymbolInfoProvider {
  @SuppressWarnings("UnstableApiUsage")
  @Override
  public @Nullable Pair<@NotNull SymbolKind, @NotNull String> symbolInfo(@NotNull PsiElement elem) {
    if (elem instanceof final PsiClass elemClass) {
      return getPair(() -> elemClass.isAnnotationType() || elemClass.isInterface() ? SymbolKind.Interface
              : elemClass.isEnum() ? SymbolKind.Enum
              : SymbolKind.Class,
          () -> elemClass.getName() != null ? elemClass.getName()
              : elemClass.getQualifiedName() != null ? elemClass.getQualifiedName()
              : "<anonymous>");
    } else if (elem instanceof final PsiClassInitializer elemInit) {
      return getPair(() -> SymbolKind.Constructor,
          () -> elemInit.getName() == null ? "<init>" : elemInit.getName());
    } else if (elem instanceof final PsiMethod elemMethod) {
      return getPair(() -> elemMethod.isConstructor() ? SymbolKind.Constructor : SymbolKind.Method,
          () -> methodLabel(elemMethod));
    } else if (elem instanceof PsiEnumConstant) {
      return getPair(() -> SymbolKind.EnumMember,
          ((PsiEnumConstant) elem)::getName);
    } else if (elem instanceof final PsiField elemField) {
      return getPair(() -> elemField.hasModifier(JvmModifier.STATIC) && elemField.hasModifier(JvmModifier.FINAL)
          ? SymbolKind.Constant : SymbolKind.Field, elemField::getName);
    } else if (elem instanceof final PsiVariable elemVar) {
      return getPair(() -> SymbolKind.Variable,
          () -> elemVar.getName() == null ? "<unknown>" : elemVar.getName());
    }
    return null;
  }
}
