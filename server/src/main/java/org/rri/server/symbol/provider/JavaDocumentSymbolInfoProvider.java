package org.rri.server.symbol.provider;

import com.intellij.lang.jvm.JvmModifier;
import com.intellij.psi.*;
import org.eclipse.lsp4j.SymbolKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.rri.server.symbol.provider.DocumentSymbolInfoProvider.Info.composeInfo;

public class JavaDocumentSymbolInfoProvider extends JVMDocumentSymbolInfoProvider {
  @SuppressWarnings("UnstableApiUsage")
  @Override
  public @Nullable Info calculateSymbolInfo(@NotNull PsiElement elem) {
    if (elem instanceof final PsiClass elemClass) {
      return composeInfo(elemClass.isAnnotationType() || elemClass.isInterface() ? SymbolKind.Interface
              : elemClass.isEnum() ? SymbolKind.Enum
              : SymbolKind.Class,
          () -> elemClass.getName() != null ? elemClass.getName()
              : elemClass.getQualifiedName() != null ? elemClass.getQualifiedName()
              : "<anonymous>");
    } else if (elem instanceof final PsiClassInitializer elemInit) {
      return composeInfo(SymbolKind.Constructor,
          () -> elemInit.getName() == null ? "<init>" : elemInit.getName());
    } else if (elem instanceof final PsiMethod elemMethod) {
      return composeInfo(elemMethod.isConstructor() ? SymbolKind.Constructor : SymbolKind.Method,
          () -> methodLabel(elemMethod));
    } else if (elem instanceof PsiEnumConstant) {
      return composeInfo(SymbolKind.EnumMember,
          ((PsiEnumConstant) elem)::getName);
    } else if (elem instanceof final PsiField elemField) {
      return composeInfo(elemField.hasModifier(JvmModifier.STATIC) && elemField.hasModifier(JvmModifier.FINAL)
          ? SymbolKind.Constant : SymbolKind.Field, elemField::getName);
    } else if (elem instanceof final PsiVariable elemVar) {
      return composeInfo(SymbolKind.Variable,
          () -> elemVar.getName() == null ? "<unknown>" : elemVar.getName());
    }
    return null;
  }
}
