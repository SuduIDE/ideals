package org.rri.server.completions;

import com.intellij.codeInsight.completion.CompletionProcess;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/* todo
    Find an alternative way to find Indicator from project for completion
    This process is needed for creation Completion Parameters and insertDummyIdentifier call.
*/
public class VoidCompletionProcess extends AbstractProgressIndicatorExBase implements Disposable, CompletionProcess {
  @Override
  public boolean isAutopopupCompletion() {
    return false;
  }

  // todo check that we don't need this lock
  @NotNull
  private final Object myLock = ObjectUtils.sentinel("VoidCompletionProcess");

  @Override
  public void dispose() {
  }

  void registerChildDisposable(@NotNull Supplier<Disposable> child) {
    synchronized (myLock) {
      // Idea developer says: "avoid registering stuff on an indicator being disposed concurrently"
      checkCanceled();
      Disposer.register(this, child.get());
    }
  }
}
