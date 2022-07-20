package org.rri.server.commands;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.server.LspPath;
import org.rri.server.MyTextDocumentService;
import org.rri.server.util.MiscUtil;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class LspCommand<R> implements Function<ExecutorContext, R>, Disposable {
    protected static final Logger LOG = Logger.getInstance(MyTextDocumentService.class);

    @Override
    public void dispose() {
        // Do nothing
    }

    public @NotNull CompletableFuture<@Nullable R> invokeAndGetFuture(@NotNull TextDocumentPositionParams params,
                                                                      @NotNull Project project,
                                                                      @NotNull Supplier<@NotNull String> message,
                                                                      boolean withCancelToken) {
        final var path = LspPath.fromLspUri(params.getTextDocument().getUri());
        final var virtualFile = path.findVirtualFile();
        if (virtualFile == null) {
            LOG.info("File not found: " + path);
            // todo mb need to throw excep
            return CompletableFuture.completedFuture(null);
        }

        final var app = ApplicationManager.getApplication();

        LOG.info(message.get());
        Executor executor = AppExecutorUtil.getAppExecutorService();
        if (withCancelToken) {
            return CompletableFutures.computeAsync(executor, cancelToken -> getResult(app, path, project, cancelToken));
        } else {
            return CompletableFuture.supplyAsync(() -> getResult(app, path, project, null), executor);
        }
    }

    private @Nullable R getResult(@NotNull Application app, @NotNull LspPath path, Project project, @Nullable CancelChecker cancelToken) {
        final AtomicReference<R> ref = new AtomicReference<>();
        app.invokeAndWait(() -> MiscUtil.withPsiFileInReadAction(
                project,
                path,
                (psiFile) -> {
                    final var execCtx = new ExecutorContext(psiFile, project, cancelToken);
                    ref.set(apply(execCtx));
                }
        ), app.getDefaultModalityState());
        return ref.get();
    }
}
