package org.rri.ideals.server.commands;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.util.MiscUtil;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public abstract class LspCommand<R> implements Disposable {
  private static final Logger LOG = Logger.getInstance(LspCommand.class);

  @NotNull
  protected abstract Supplier<@NotNull String> getMessageSupplier();

  protected abstract boolean isCancellable();

  protected abstract R execute(@NotNull ExecutorContext ctx);

  @Override
  public void dispose() {
    // Do nothing
  }

  public @NotNull CompletableFuture<@Nullable R> runAsync(@NotNull Project project, @NotNull LspPath path) {
    final var virtualFile = path.findVirtualFile();
    if (virtualFile == null) {
      LOG.info("File not found: " + path);
      // todo mb need to throw excep
      return CompletableFuture.completedFuture(null);
    }

    LOG.info(getMessageSupplier().get());
    Executor executor = AppExecutorUtil.getAppExecutorService();
    if (isCancellable()) {
      return CompletableFutures.computeAsync(executor, cancelToken -> getResult(path, project, cancelToken));
    } else {
      return CompletableFuture.supplyAsync(() -> getResult(path, project, null), executor);
    }
  }

  private @Nullable R getResult(@NotNull LspPath path,
                                @NotNull Project project,
                                @Nullable CancelChecker cancelToken) {
    final AtomicReference<R> ref = new AtomicReference<>();
    ApplicationManager.getApplication()
        .invokeAndWait(
            () -> ref.set(MiscUtil.produceWithPsiFileInReadAction(
                project,
                path,
                (psiFile) -> execute(new ExecutorContext(psiFile, project, cancelToken))
            )));
    return ref.get();
  }
}
