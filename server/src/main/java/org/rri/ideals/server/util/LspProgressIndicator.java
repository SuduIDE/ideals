package org.rri.ideals.server.util;

import com.intellij.openapi.progress.EmptyProgressIndicatorBase;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.StandardProgressIndicator;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.jetbrains.annotations.NotNull;

public class LspProgressIndicator extends EmptyProgressIndicatorBase implements StandardProgressIndicator {
  @NotNull
  private final CancelChecker cancelChecker;
  private volatile boolean myIsCanceled;

  public LspProgressIndicator(@NotNull CancelChecker cancelChecker) {
    this.cancelChecker = cancelChecker;
  }


  @Override
  public void start() {
    super.start();
    myIsCanceled = false;
  }

  @Override
  public void cancel() {
    myIsCanceled = true;
    ProgressManager.canceled(this);
  }

  @Override
  public boolean isCanceled() {
    return myIsCanceled || cancelChecker.isCanceled();
  }
}
