package org.rri.server;

import com.intellij.psi.PsiElement;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.jetbrains.annotations.NotNull;
import org.rri.server.symbol.WorkspaceSymbolService;
import org.rri.server.util.MiscUtil;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

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

  private final Map<WorkspaceSymbol, PsiElement> elements = new ConcurrentHashMap<>();

  @SuppressWarnings("deprecation")
  @Override
  public CompletableFuture<Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>> symbol(WorkspaceSymbolParams params) {
    return new WorkspaceSymbolService(params.getQuery()).runAsync(session.getProject(), elements);
  }

  @Override
  public CompletableFuture<WorkspaceSymbol> resolveWorkspaceSymbol(WorkspaceSymbol workspaceSymbol) {
    return CompletableFuture.supplyAsync(() -> {
      workspaceSymbol.setLocation(Either.forLeft(MiscUtil.psiElementToLocation(elements.get(workspaceSymbol))));
      return workspaceSymbol;
    });
  }
}
