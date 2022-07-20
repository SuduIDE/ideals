package org.rri.server;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.server.commands.ExecutorContext;
import org.rri.server.commands.LspCommand;
import org.rri.server.completions.CompletionsService;
import org.rri.server.diagnostics.DiagnosticsService;
import org.rri.server.references.FindDefinitionCommand;
import org.rri.server.references.FindTypeDefinitionCommand;
import org.rri.server.references.FindUsagesCommand;
import org.rri.server.util.Metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params) {
    return new FindDefinitionCommand(params.getPosition())
            .runAsync(session.getProject(), LspPath.fromLspUri(params.getTextDocument().getUri()));
  }

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> typeDefinition(TypeDefinitionParams params) {
    return new FindTypeDefinitionCommand(params.getPosition())
            .runAsync(session.getProject(), LspPath.fromLspUri(params.getTextDocument().getUri()));
  }

  @Override
  public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
    return new FindUsagesCommand(params.getPosition())
            .runAsync(session.getProject(), LspPath.fromLspUri(params.getTextDocument().getUri()));
  }

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params) {
    final var command = new FindDefinitionCommand(params.getPosition());
    return command.invokeAndGetFuture(params, session.getProject(), () -> "Definition call", false);
  }

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> typeDefinition(TypeDefinitionParams params) {
    final var command = new FindTypeDefinitionCommand(params.getPosition());
    return command.invokeAndGetFuture(params, session.getProject(), () -> "TypeDefinition call", false);
  }

  @Override
  public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
    final var command = new FindUsagesCommand(params.getPosition());
    return command.invokeAndGetFuture(params, session.getProject(), () -> "References (Find usages) call", true);
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
  private CompletionsService completions() {
    return session.getProject().getService(CompletionsService.class);
  }

  @Override
  @NotNull
  public CompletableFuture<CompletionItem> resolveCompletionItem(@NotNull CompletionItem unresolved) {
    // todo currently "completion resolve" == "insert completion item label"
    return CompletableFuture.completedFuture(unresolved);
  }

  @Override
  @NotNull
  public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(@NotNull CompletionParams params) {
    final var path = LspPath.fromLspUri(params.getTextDocument().getUri());

    final var virtualFile = path.findVirtualFile();
    if (virtualFile == null) {
      LOG.info("File not found: " + path);
      // todo Maybe we need to throw exception
      return CompletableFuture.completedFuture(Either.forLeft(new ArrayList<>()));
    }
    return completions().startCompletionCalculation(path, params.getPosition());
  }

  private <R> CompletableFuture<R> invokeAndGetFuture(TextDocumentPositionParams params, LspCommand<R> command, Supplier<String> message, boolean withCancelToken) {
    final var path = LspPath.fromLspUri(params.getTextDocument().getUri());
    final var virtualFile = path.findVirtualFile();
    if (virtualFile == null) {
      LOG.info("File not found: " + path);
      // todo mb need to throw excep
      return null;
    }

    final var app = ApplicationManager.getApplication();
    final var context = LspContext.getContext(session.getProject());

    LOG.info(message.get());
    if (withCancelToken) {
      return CompletableFutures.computeAsync(cancelToken -> getResult(app, path, context, command, cancelToken));
    } else {
      return CompletableFuture.supplyAsync(() -> getResult(app, path, context, command, null));
    }
  }

  private <R> @Nullable R getResult(@NotNull Application app, @NotNull LspPath path, @NotNull LspCommand<R> command, @Nullable CancelChecker cancelToken) {
    final AtomicReference<R> ref = new AtomicReference<>();
    app.invokeAndWait(() -> MiscUtil.withPsiFileInReadAction(
            session.getProject(),
            path,
            (psiFile) -> {
              final var execCtx = new ExecutorContext(psiFile, session.getProject(), cancelToken);
              ref.set(command.apply(execCtx));
            }
    ), app.getDefaultModalityState());
    return ref.get();
  }
}
