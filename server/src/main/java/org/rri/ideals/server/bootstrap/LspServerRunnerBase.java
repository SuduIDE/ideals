package org.rri.ideals.server.bootstrap;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.CoreIconManager;
import com.intellij.ui.IconManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.LspServer;
import org.rri.ideals.server.MyLanguageClient;
import org.rri.ideals.server.util.MiscUtil;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

public abstract class LspServerRunnerBase {
  private final static Logger LOG = Logger.getInstance(LspServerRunnerBase.class);

  private final boolean isMultiConnection;

  protected LspServerRunnerBase(boolean isMultiConnection) {
    this.isMultiConnection = isMultiConnection;
  }

  @NotNull
  protected abstract Connection waitForConnection();

  protected abstract void prepareForListening();

  protected record Connection(@NotNull InputStream input, @NotNull OutputStream output) {}

  private ExecutorService createServerThreads() {
    return Executors.newCachedThreadPool();
  }

  public CompletableFuture<Void> launch() {
    prepareForListening();
    try {
      //noinspection UnstableApiUsage
      IconManager.activate(new CoreIconManager());
    } catch (Throwable e) {
      LOG.warn("Core icon manager can't be loaded:\n" + e);
    }
    return CompletableFuture.runAsync(() -> {
      while (true) {
        var serverFuture = connectServer(waitForConnection());
        if (!isMultiConnection) {
          serverFuture.join();
          break;
        }
      }
    }, AppExecutorUtil.getAppExecutorService());
  }

  private CompletableFuture<Void> connectServer(@NotNull Connection connection) {
    Function<MessageConsumer, MessageConsumer> wrapper = consumer -> consumer;

    var languageServer = new LspServer();
    var launcher = Launcher.createIoLauncher(
        languageServer, MyLanguageClient.class,
        connection.input, connection.output, createServerThreads(), wrapper
    );
    var client = launcher.getRemoteProxy();
    languageServer.connect(client);
    LOG.info("Listening for commands.");
    return CompletableFuture
        .runAsync(MiscUtil.asRunnable(() -> launcher.startListening().get()), AppExecutorUtil.getAppExecutorService())
        .whenComplete((ignored1, ignored2) -> languageServer.stop());
  }
}
