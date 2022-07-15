package org.rri.server;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.jetbrains.annotations.NotNull;
import org.rri.server.completions.MyCompletionsService;
import org.rri.server.diagnostics.DiagnosticsService;
import org.rri.server.util.Metrics;
import org.rri.server.util.MiscUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class MyTextDocumentService implements TextDocumentService {

  private static final Logger LOG = Logger.getInstance(MyTextDocumentService.class);
  private final @NotNull LspSession session;

  public MyTextDocumentService(@NotNull LspSession session) {
    this.session = session;
  }

  @Override
  public void didOpen(DidOpenTextDocumentParams params) {
    final var textDocument = params.getTextDocument();

    final var path = LspPath.fromLspUri(textDocument.getUri());

    Metrics.run(() -> "didOpen: " + path, () -> {
      documents().startManaging(textDocument);
      diagnostics().launchDiagnostics(path);

      if (DumbService.isDumb(session.getProject())) {
        LOG.debug("Sending indexing started: " + path);
        LspContext.getContext(session.getProject()).getClient().notifyIndexStarted();
      }
  /*  todo
        val projectSdk = ProjectRootManager.getInstance(project).projectSdk
        if (projectSdk == null) {
          warnNoJdk(client)
        }
*/
    });
  }

  @Override
  public void didChange(DidChangeTextDocumentParams params) {
    final var path = LspPath.fromLspUri(params.getTextDocument().getUri());

    Metrics.run(() -> "didChange: " + path, () -> {
      documents().updateDocument(params);
      diagnostics().launchDiagnostics(path);
    });
  }

  @Override
  public void didClose(DidCloseTextDocumentParams params) {
    documents().stopManaging(params.getTextDocument());
  }

  @Override
  public void didSave(DidSaveTextDocumentParams params) {
    documents().syncDocument(params.getTextDocument());
    diagnostics().launchDiagnostics(LspPath.fromLspUri(params.getTextDocument().getUri()));
  }

  @Override
  public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(DocumentHighlightParams params) {
    return TextDocumentService.super.documentHighlight(params);
  }

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params) {
    /*return CompletableFuture.supplyAsync(() -> {

    });*/
    return null;
  }

  public void refreshDiagnostics() {
    LOG.info("Start refreshing diagnostics for all opened documents");
    documents().forEach(diagnostics()::launchDiagnostics);
  }

  @NotNull
  private ManagedDocuments documents() {
    return session.getProject().getService(ManagedDocuments.class);
  }

  @NotNull
  private DiagnosticsService diagnostics() {
    return session.getProject().getService(DiagnosticsService.class);
  }

  @NotNull
  private MyCompletionsService completions() {
    return session.getProject().getService(MyCompletionsService.class);
  }

  @Override
  public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
    // while without addition completion info
    return CompletableFuture.completedFuture(unresolved);
  }

  @Override
  public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
    var app = ApplicationManager.getApplication();
    final var path = LspPath.fromLspUri(params.getTextDocument().getUri());

    final var virtualFile = path.findVirtualFile();
    if (virtualFile == null) {
      LOG.info("File not found: " + path);
      // todo mb need to throw excep
      return CompletableFuture.completedFuture(Either.forLeft(new ArrayList<>()));
    }

    return CompletableFutures.computeAsync(
            (cancelChecker) -> {
              final AtomicReference<Either<List<CompletionItem>, CompletionList>> ref = new AtomicReference<>();
              app.invokeAndWait(
                      () -> MiscUtil.withPsiFileInReadAction(
                              session.getProject(),
                              path,
                              (psiFile) ->
                                      ref.set(completions().launchCompletions(psiFile, params.getPosition(), cancelChecker))
                      ),
                      app.getDefaultModalityState()
              );
              return ref.get();
            }
    );
  }
}
