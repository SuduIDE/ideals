package org.rri.server;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public class TestUtil {
  private TestUtil() {
  }

  @SuppressWarnings("UnusedReturnValue")
  public static <T> T getNonBlockingEdt(@NotNull CompletableFuture<T> future) {
    if(ApplicationManager.getApplication().isDispatchThread()) {
      waitInEdtFor(future::isDone);
    }
    return future.join();
  }

  public static void waitInEdt(long timeInMs) {
    final var mark = System.nanoTime();
    waitInEdtFor (() -> (System.nanoTime() - mark)/1_000_000 >= timeInMs);
  }


  public static void waitInEdtFor(@NotNull Supplier<Boolean> condition) {
    while (!condition.get()) {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      Thread.yield();
    }
  }

}
