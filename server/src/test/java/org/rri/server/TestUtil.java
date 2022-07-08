package org.rri.server;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class TestUtil {
  private TestUtil() {
  }

  public static <T> T edtSafeGet(@NotNull CompletableFuture<T> future) {
    if(ApplicationManager.getApplication().isDispatchThread()) {
      while (!future.isDone()) {
        IdeEventQueue.getInstance().flushQueue();
        Thread.yield();
      }
    }
    return future.join();
  }
}
