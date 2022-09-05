package org.rri.ideals.server.formatting;

import org.eclipse.lsp4j.FormattingOptions;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.util.MiscUtil;

public final class FormattingTestUtil {
  private FormattingTestUtil() {
  }

  @NotNull
  public static FormattingOptions defaultOptions() {
    return MiscUtil.with(
        new FormattingOptions(),
        formattingOptions -> {
          formattingOptions.setInsertFinalNewline(true);
          formattingOptions.setInsertSpaces(true);
          formattingOptions.setTabSize(4);
        });
  }

}
