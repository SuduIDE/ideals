package org.rri.server.formatting;

import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
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

  public static class DumbCancelChecker implements CancelChecker {

    @Override
    public void checkCanceled() {}

    @Override
    public boolean isCanceled() {
      return false;
    }
  }
}
