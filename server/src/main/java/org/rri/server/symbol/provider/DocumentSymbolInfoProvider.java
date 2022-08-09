package org.rri.server.symbol.provider;

import com.intellij.psi.PsiElement;
import org.eclipse.lsp4j.SymbolKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DocumentSymbolInfoProvider {
  @Nullable SymbolKind symbolKind(@NotNull PsiElement elem);

  @Nullable String symbolName(@NotNull PsiElement elem);

  default boolean isDeprecated(@NotNull PsiElement elem) {
    return false;
  }
}
