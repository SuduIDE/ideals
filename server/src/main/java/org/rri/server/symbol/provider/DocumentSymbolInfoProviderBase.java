package org.rri.server.symbol.provider;

import com.intellij.openapi.util.Pair;
import org.eclipse.lsp4j.SymbolKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public abstract class DocumentSymbolInfoProviderBase implements DocumentSymbolInfoProvider {
  @Nullable
  static Pair<@NotNull SymbolKind, @NotNull String> getPair(@NotNull Supplier<@Nullable SymbolKind> symbolKind,
                                                            @NotNull Supplier<@Nullable String> symbolName) {
    final SymbolKind kind;
    final String name;
    return (kind = symbolKind.get()) == null ? null
        : (name = symbolName.get()) == null ? null
        : new Pair<>(kind, name);
  }
}
