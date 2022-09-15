package org.rri.ideals.server.bootstrap;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.ideals.server.util.MiscUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

class TcpLspServerRunner extends LspServerRunnerBase {
  private final static Logger LOG = Logger.getInstance(TcpLspServerRunner.class);

  private int port = 8989;  // default port

  @Nullable
  private ServerSocket serverSocket = null;

  TcpLspServerRunner() {
    super(true);
  }


  public void setPort(int port) {
    this.port = port;
  }

  @Override
  protected void prepareForListening() {
    LOG.info("Starting the LSP server on port: " + port);
    try {
      serverSocket = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  @NotNull
  protected Connection waitForConnection() {
    assert serverSocket != null;
    try {
      var clientSocket = serverSocket.accept();
      return new Connection(clientSocket.getInputStream(), clientSocket.getOutputStream());
    } catch (Exception e) {
      LOG.error("Socket connection error: " + e);
      closeServerSocket();
      throw MiscUtil.wrap(e);
    }
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
