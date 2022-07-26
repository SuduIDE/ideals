package org.rri.server;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;
import org.rri.server.util.MiscUtil;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public class TestUtil {
  private TestUtil() {
  }

  @SuppressWarnings("UnusedReturnValue")
  public static <T> T getNonBlockingEdt(@NotNull CompletableFuture<T> future, long timeoutMs) {
    final var mark = System.nanoTime();
    if (ApplicationManager.getApplication().isDispatchThread()) {
      waitInEdtFor(future::isDone, timeoutMs);
    }
    return MiscUtil.makeThrowsUnchecked(() -> future.get(timeoutMs, TimeUnit.MILLISECONDS));
  }

  public static void waitInEdtFor(@NotNull Supplier<Boolean> condition, long timeoutMs) {
    final var mark = System.nanoTime();
    while (!condition.get()) {
      if ((System.nanoTime() - mark) / 1_000_000 >= timeoutMs)
        throw new RuntimeException("timeout: " + timeoutMs, new TimeoutException());

      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      Thread.yield();
    }
  }

}
