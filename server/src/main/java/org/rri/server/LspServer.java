package org.rri.server;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.server.util.Metrics;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import static org.rri.server.util.MiscUtil.with;

public class LspServer implements LanguageServer, LanguageClientAware, LspSession, DumbService.DumbModeListener {
  private final static Logger LOG = Logger.getInstance(LspServer.class);
  private final MyTextDocumentService myTextDocumentService = new MyTextDocumentService(this);
  private final MyWorkspaceService myWorkspaceService = new MyWorkspaceService(this);
  @Nullable
  private MyLanguageClient client = null;

  @Nullable
  private Project project = null;

  public CompletableFuture<InitializeResult> initialize(@NotNull InitializeParams params) {
    final var workspaceFolders = params.getWorkspaceFolders();
    if(workspaceFolders == null) {
      return CompletableFuture.completedFuture(new InitializeResult(defaultServerCapabilities()));
    }

    //   // todo how about multiple folders
    final var projectRoot = LspPath.fromLspUri(workspaceFolders.get(0).getUri());

    return CompletableFuture.supplyAsync( () -> {
      Metrics.run(() -> "initialize: " + projectRoot, () -> {
        project = ProjectService.getInstance().resolveProjectFromRoot(projectRoot);

        assert client != null;
        LspContext.createContext(project, client, params.getCapabilities());
        project.getMessageBus().connect().subscribe(DumbService.DUMB_MODE, this);

        LOG.info("LSP was initialized. Project: " + project);
      });

      return new InitializeResult(defaultServerCapabilities());
    });

  }

  @NotNull
  private ServerCapabilities defaultServerCapabilities() {

    return with(new ServerCapabilities(), (it) -> {
      it.setTextDocumentSync(with(new TextDocumentSyncOptions(), (syncOptions) -> {
        syncOptions.setOpenClose(true);
        syncOptions.setChange(TextDocumentSyncKind.Incremental);
        syncOptions.setSave(new SaveOptions(true));
      }));

//      it.setHoverProvider(true);
      it.setCompletionProvider(new CompletionOptions(true, Arrays.asList(".", "@", "#")));
//      it.setSignatureHelpProvider(null);
//      it.setDefinitionProvider(true);
//      it.setTypeDefinitionP rovider(false);
//      it.setImplementationProvider(true);
//      it.setReferencesProvider(true);
//      it.setDocumentHighlightProvider(true);
//      it.setDocumentSymbolProvider(true);
//      it.setWorkspaceSymbolProvider(true);
//      it.setCodeActionProvider(false);
//      it.setCodeLensProvider(new CodeLensOptions(false));
//      it.setDocumentFormattingProvider(true);
//      it.setDocumentRangeFormattingProvider(true);
//      it.setDocumentOnTypeFormattingProvider(null);
//      it.setRenameProvider(false);
//      it.setDocumentLinkProvider(null);
//      it.setExecuteCommandProvider(new ExecuteCommandOptions());
      it.setExperimental(null);

    });
  }

  public CompletableFuture<Object> shutdown()  {
    return CompletableFuture.supplyAsync(() -> {
      stop();
      return null;
    });
  }

  public void exit() {
    stop();
  }

  public void stop() {
    if(project != null) {
      ProjectService.getInstance().closeProject(project);
      this.project = null;
    }
  }

  @Override
  public MyTextDocumentService getTextDocumentService() {
    return myTextDocumentService;
  }

  @Override
  public MyWorkspaceService getWorkspaceService() {
    return myWorkspaceService;
  }

  @Override
  public void connect(@NotNull LanguageClient client) {
    assert client instanceof MyLanguageClient;
    this.client = (MyLanguageClient) client;
  }

  @NotNull
  private MyLanguageClient getClient() {
    assert client != null;
    return client;
  }

  @NotNull
  @Override
  public Project getProject() {
    if(project == null)
      throw new IllegalStateException("LSP session is not yet initialized");
    return project;
  }

  @Override
  public void enteredDumbMode() {
    LOG.info("Entered dumb mode. Notifying client...");
    getClient().notifyIndexStarted();
  }

  @Override
  public void exitDumbMode() {
    LOG.info("Exited dumb mode. Refreshing diagnostics...");
    getClient().notifyIndexFinished();
    getTextDocumentService().refreshDiagnostics();
  }

}