package org.rri.ideals.server.bootstrap;

import com.intellij.openapi.diagnostic.LogLevel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.jetbrains.annotations.Nullable;
import org.rri.ideals.server.LspServer;
import org.rri.ideals.server.MyLanguageClient;
import org.rri.ideals.server.util.MiscUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;

public class LspServerRunner {
  private final static Logger LOG = Logger.getInstance(LspServerRunner.class);

  @Nullable
  private ServerSocket serverSocket = null;

  public CompletableFuture<Void> launch(int port) {
    if (serverSocket != null) {
      return CompletableFuture.completedFuture(null);
    }

    LOG.info("Starting the LSP server on port: " + port);
    try {
      serverSocket = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return CompletableFuture.runAsync(() -> {
      //noinspection InfiniteLoopStatement
      while (true) {
        try {
          var clientSocket = serverSocket.accept();
          connect(clientSocket);
        } catch (IOException e) {
          LOG.error("Socket connection error: " + e);
          closeServerSocket();
          throw new RuntimeException(e);
        }
      }
    }, AppExecutorUtil.getAppExecutorService());
  }

  private void connect(Socket connection) throws IOException {
    var languageServer = new LspServer();
    LogPrintWriter trace = new LogPrintWriter(Logger.getInstance("org.eclipse.lsp4j"), LogLevel.TRACE);
    var launcher = Launcher.createLauncher(
            languageServer, MyLanguageClient.class,
            connection.getInputStream(), connection.getOutputStream(), false, trace
    );
    var client = launcher.getRemoteProxy();
    languageServer.connect(client);
    LOG.info("Listening for commands.");
    CompletableFuture
            .runAsync(MiscUtil.asRunnable(() -> launcher.startListening().get()))
            .whenComplete((ignored1, ignored2) -> languageServer.stop());
  }

  private void closeServerSocket() {
    if (serverSocket != null) {
      try {
        LOG.info("Close language server socket port " + serverSocket.getLocalPort());
        serverSocket.close();
      } catch (IOException e) {
        LOG.error("Close ServerSocket exception: " + e);
      }

    }
    serverSocket = null;
  }
}
