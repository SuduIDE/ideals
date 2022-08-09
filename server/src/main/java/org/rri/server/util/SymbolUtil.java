package org.rri.server.util;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.jetbrains.python.PythonLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.KotlinLanguage;
import org.rri.server.symbol.provider.DocumentSymbolProvider;
import org.rri.server.symbol.provider.JavaDocumentSymbolProvider;
import org.rri.server.symbol.provider.KtDocumentSymbolProvider;
import org.rri.server.symbol.provider.PyDocumentSymbolProvider;

import java.util.Map;
import java.util.function.Supplier;

public class SymbolUtil {
  @NotNull
  private static final Map<@NotNull String, @NotNull Supplier<@NotNull DocumentSymbolProvider>> mapDocSymProvider;

  static {
    mapDocSymProvider = Map.of(PythonLanguage.INSTANCE.getID(), PyDocumentSymbolProvider::new,
        KotlinLanguage.INSTANCE.getID(), KtDocumentSymbolProvider::new,
        JavaLanguage.INSTANCE.getID(), JavaDocumentSymbolProvider::new);
  }

  @Nullable
  public static DocumentSymbolProvider getDocumentSymbolProvider(@NotNull Language lang) {
    final var supplier = mapDocSymProvider.get(lang.getID());
    return supplier == null ? null : supplier.get();
  }
}
