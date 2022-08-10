package org.rri.server.symbol.provider;

import com.intellij.psi.PsiElement;
import org.eclipse.lsp4j.SymbolKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public interface DocumentSymbolInfoProvider {
  class Info {
    @NotNull
    private final String name;
    @NotNull
    private final SymbolKind kind;

    private Info(@NotNull String name, @NotNull SymbolKind kind) {
      this.name = name;
      this.kind = kind;
    }

    @Nullable
    public static Info composeInfo(@Nullable SymbolKind kind,
                                   @NotNull Supplier<@Nullable String> calculateSymbolName) {
      final String name;
      return kind == null ? null
          : (name = calculateSymbolName.get()) == null ? null
          : new Info(name, kind);
    }

    @NotNull
    public String getName() {
      return name;
    }

    @NotNull
    public SymbolKind getKind() {
      return kind;
    }
  }

  @Nullable Info calculateSymbolInfo(@NotNull PsiElement elem);

  default boolean isDeprecated(@NotNull PsiElement elem) {
    return false;
  }
}
