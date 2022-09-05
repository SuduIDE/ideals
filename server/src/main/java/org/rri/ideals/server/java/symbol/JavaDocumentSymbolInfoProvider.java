package org.rri.ideals.server.java.symbol;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.psi.*;
import org.eclipse.lsp4j.SymbolKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.ideals.server.symbol.DocumentSymbolInfoProvider;

public class JavaDocumentSymbolInfoProvider extends JVMDocumentSymbolInfoProvider {
  @Override
  public @NotNull Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  @SuppressWarnings("UnstableApiUsage")
  @Override
  public @Nullable DocumentSymbolInfoProvider.Info calculateSymbolInfo(@NotNull PsiElement elem) {
    if (elem instanceof final PsiClass elemClass) {
      return DocumentSymbolInfoProvider.Info.composeInfo(elemClass.isAnnotationType() || elemClass.isInterface() ? SymbolKind.Interface
              : elemClass.isEnum() ? SymbolKind.Enum
              : SymbolKind.Class,
          () -> elemClass.getName() != null ? elemClass.getName()
              : elemClass.getQualifiedName() != null ? elemClass.getQualifiedName()
              : "<anonymous>");
    } else if (elem instanceof final PsiClassInitializer elemInit) {
      return DocumentSymbolInfoProvider.Info.composeInfo(SymbolKind.Constructor,
          () -> elemInit.getName() == null ? "<init>" : elemInit.getName());
    } else if (elem instanceof final PsiMethod elemMethod) {
      return DocumentSymbolInfoProvider.Info.composeInfo(elemMethod.isConstructor() ? SymbolKind.Constructor : SymbolKind.Method,
          () -> methodLabel(elemMethod));
    } else if (elem instanceof PsiEnumConstant) {
      return DocumentSymbolInfoProvider.Info.composeInfo(SymbolKind.EnumMember,
          ((PsiEnumConstant) elem)::getName);
    } else if (elem instanceof final PsiField elemField) {
      return DocumentSymbolInfoProvider.Info.composeInfo(elemField.hasModifier(JvmModifier.STATIC) && elemField.hasModifier(JvmModifier.FINAL)
          ? SymbolKind.Constant : SymbolKind.Field, elemField::getName);
    } else if (elem instanceof final PsiVariable elemVar) {
      return DocumentSymbolInfoProvider.Info.composeInfo(SymbolKind.Variable,
          () -> elemVar.getName() == null ? "<unknown>" : elemVar.getName());
    }
    return null;
  }
}
