package org.rri.server;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.jetbrains.annotations.NotNull;
import org.rri.server.symbol.WorkspaceSymbolService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MyWorkspaceService implements WorkspaceService {
  @NotNull
  private final LspSession session;

  public MyWorkspaceService(@NotNull LspSession session) {
    this.session = session;
  }

  @Override
  public void didChangeConfiguration(DidChangeConfigurationParams params) {

  }

  @Override
  public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {

  }

  private @NotNull WorkspaceSymbolService workspaceSymbol() {
    return session.getProject().getService(WorkspaceSymbolService.class);
  }

  @SuppressWarnings("deprecation")
  @Override
  public CompletableFuture<Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>> symbol(WorkspaceSymbolParams params) {
    return workspaceSymbol().runSearch(params.getQuery());
  }

  @Override
  public CompletableFuture<WorkspaceSymbol> resolveWorkspaceSymbol(WorkspaceSymbol workspaceSymbol) {
    return workspaceSymbol().resolveWorkspaceSymbol(workspaceSymbol);
  }
}
