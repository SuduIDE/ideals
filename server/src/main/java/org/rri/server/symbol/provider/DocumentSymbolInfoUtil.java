package org.rri.server.symbol.provider;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.jetbrains.python.PythonLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.KotlinLanguage;

import java.util.Map;
import java.util.function.Supplier;

public class DocumentSymbolInfoUtil {
  @NotNull
  private static final Map<@NotNull String, @NotNull Supplier<@NotNull DocumentSymbolInfoProvider>> mapDocSymProvider;

  static {
    mapDocSymProvider = Map.of(PythonLanguage.INSTANCE.getID(), PyDocumentSymbolInfoProvider::new,
        KotlinLanguage.INSTANCE.getID(), KtDocumentSymbolInfoProvider::new,
        JavaLanguage.INSTANCE.getID(), JavaDocumentSymbolInfoProvider::new);
  }

  @Nullable
  public static DocumentSymbolInfoProvider getDocumentSymbolProvider(@NotNull Language lang) {
    final var supplier = mapDocSymProvider.get(lang.getID());
    return supplier == null ? null : supplier.get();
  }
}
