package org.rri.ideals.server.bootstrap;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

class StdioLspServerRunner extends LspServerRunnerBase {
  private final static Logger LOG = Logger.getInstance(StdioLspServerRunner.class);

  StdioLspServerRunner() {
    super(false);
  }

  @Override
  protected void prepareForListening() {
    LOG.info("Starting the LSP server on stdio");
  }

  @Override
  @NotNull
  protected Connection waitForConnection() {
    return new Connection(System.in, System.out);
  }
}
