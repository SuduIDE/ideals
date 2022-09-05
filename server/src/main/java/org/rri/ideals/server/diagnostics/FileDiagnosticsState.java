package org.rri.ideals.server.diagnostics;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ScheduledFuture;

final class FileDiagnosticsState {
  private final @NotNull PsiFile file;
  private final @NotNull QuickFixRegistry quickFixes;
  private final @NotNull ScheduledFuture<?> task;

  public FileDiagnosticsState(@NotNull PsiFile file, @NotNull QuickFixRegistry quickFixes, @NotNull ScheduledFuture<?> task) {
    this.file = file;
    this.task = task;
    this.quickFixes = quickFixes;
  }

  void halt() {
    // to initiate ProcessCancelledException in a running highlighting
    DaemonCodeAnalyzer.getInstance(file.getProject()).restart(file);

    task.cancel(true);
  }

  public @NotNull QuickFixRegistry getQuickFixes() {
    return quickFixes;
  }
}
