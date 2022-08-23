package org.rri.server.diagnostics;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ScheduledFuture;

final class FileDiagnosticsState {
  private final @NotNull QuickFixRegistry quickFixes;
  private final @NotNull ScheduledFuture<?> task;

  public FileDiagnosticsState(@NotNull QuickFixRegistry quickFixes, @NotNull ScheduledFuture<?> task) {
    this.task = task;
    this.quickFixes = quickFixes;
  }

  void halt() {
    task.cancel(true);
  }

  public @NotNull QuickFixRegistry getQuickFixes() {
    return quickFixes;
  }
}
