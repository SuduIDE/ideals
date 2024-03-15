package org.rri.ideals.server.bootstrap;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.ideals.server.util.MiscUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.Channels;

class TcpLspServerRunner extends LspServerRunnerBase {
  private final static Logger LOG = Logger.getInstance(TcpLspServerRunner.class);

  @Nullable
  private AsynchronousServerSocketChannel serverSocket;

  private int port = 8989;  // default port

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
      serverSocket = AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  @NotNull
  protected Connection waitForConnection() {
    assert serverSocket != null;
    try {
      var socketChannel = serverSocket.accept().get();
      return new Connection(Channels.newInputStream(socketChannel), Channels.newOutputStream(socketChannel));
    } catch (Exception e) {
      LOG.error("Socket connection error: " + e);
      closeServerSocket();
      throw MiscUtil.wrap(e);
    }
  }

  private void closeServerSocket() {
    if (serverSocket != null) {
      try {
        LOG.info("Close language server socket port " + ((InetSocketAddress) serverSocket.getLocalAddress()).getPort());
        serverSocket.close();
      } catch (IOException e) {
        LOG.error("Close ServerSocket exception: " + e);
      }

    }
    serverSocket = null;
  }
}
