package org.rri.server;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.jetbrains.annotations.NotNull;
import org.rri.server.completions.CompletionService;
import org.rri.server.util.MiscUtil;

import java.util.List;
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

  @NotNull
  public static List<@NotNull CompletionItem> getCompletionListAtPosition(
          @NotNull Project project, @NotNull PsiFile file, @NotNull Position position) {
    return TestUtil.getNonBlockingEdt(project.getService(CompletionService.class).startCompletionCalculation(
            LspPath.fromVirtualFile(file.getVirtualFile()), position), 3000).getLeft();
  }

  @NotNull
  public static TextDocumentIdentifier getDocumentIdentifier(@NotNull LspPath filePath) {
    return MiscUtil.with(new TextDocumentIdentifier(),
        documentIdentifier -> documentIdentifier.setUri(filePath.toLspUri()));
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
