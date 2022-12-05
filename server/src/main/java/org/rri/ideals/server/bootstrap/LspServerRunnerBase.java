package org.rri.ideals.server.bootstrap;

import com.intellij.openapi.diagnostic.LogLevel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.LspServer;
import org.rri.ideals.server.MyLanguageClient;
import org.rri.ideals.server.util.MiscUtil;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;

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

  public CompletableFuture<Void> launch() {
    prepareForListening();

    return CompletableFuture.runAsync(() -> {
      while (true) {
        var serverFuture = connectServer(waitForConnection());
        if(!isMultiConnection) {
          serverFuture.join();
          break;
        }
      }
    }, AppExecutorUtil.getAppExecutorService());
  }

  public CompletableFuture<Void> connectServer(@NotNull Connection connection) {
    var languageServer = new LspServer();
    LogPrintWriter trace = new LogPrintWriter(Logger.getInstance("org.eclipse.lsp4j"), LogLevel.TRACE);
    var launcher = Launcher.createLauncher(
        languageServer, MyLanguageClient.class,
        connection.input, connection.output, false, trace
    );
    var client = launcher.getRemoteProxy();
    languageServer.connect(client);
    LOG.info("Listening for commands.");
    return CompletableFuture
        .runAsync(MiscUtil.asRunnable(() -> launcher.startListening().get()), AppExecutorUtil.getAppExecutorService())
        .whenComplete((ignored1, ignored2) -> languageServer.stop());
  }
}
