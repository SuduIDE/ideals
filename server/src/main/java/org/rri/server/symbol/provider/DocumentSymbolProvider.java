package org.rri.server.symbol.provider;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import org.eclipse.lsp4j.SymbolKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class DocumentSymbolProvider {
  protected static final Logger LOG = Logger.getInstance(DocumentSymbolProvider.class);

  @Nullable
  public abstract SymbolKind symbolKind(@NotNull PsiElement elem);

  @Nullable
  public abstract String symbolName(@NotNull PsiElement elem);

  public boolean isDeprecated(@NotNull PsiElement elem) {
    return false;
  }
}
