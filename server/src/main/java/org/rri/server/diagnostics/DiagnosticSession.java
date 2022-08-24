package org.rri.server.diagnostics;

import org.jetbrains.annotations.NotNull;

public interface DiagnosticSession {
  @NotNull QuickFixRegistry getQuickFixRegistry();

  boolean isOutdated();

  void signalRestart();
}
