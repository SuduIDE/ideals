package org.rri.server;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.jetbrains.annotations.NotNull;

public class MyWorkspaceService implements WorkspaceService {
  public MyWorkspaceService(@NotNull LspSession session) {
  }

  @Override
  public void didChangeConfiguration(DidChangeConfigurationParams params) {

  }

  @Override
  public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {

  }
}
