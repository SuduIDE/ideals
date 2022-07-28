package org.rri.server.formatting;

import org.eclipse.lsp4j.FormattingOptions;
import org.jetbrains.annotations.NotNull;
import org.rri.server.util.MiscUtil;

public final class FormattingCommandTests {
  private FormattingCommandTests() {
  }

  @NotNull
  public static FormattingOptions defaultOptions() {
    return MiscUtil.with(
        new FormattingOptions(),
        formattingOptions -> {
          formattingOptions.setInsertSpaces(true);
          formattingOptions.setTabSize(4);
        });
  }
}
