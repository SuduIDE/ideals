package org.rri.ideals.server;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.codeactions.CodeActionService;
import org.rri.ideals.server.completions.CompletionService;
import org.rri.ideals.server.diagnostics.DiagnosticsService;
import org.rri.ideals.server.formatting.FormattingCommand;
import org.rri.ideals.server.formatting.OnTypeFormattingCommand;
import org.rri.ideals.server.references.*;
import org.rri.ideals.server.rename.RenameCommand;
import org.rri.ideals.server.signature.SignatureHelpService;
import org.rri.ideals.server.symbol.DocumentSymbolCommand;
import org.rri.ideals.server.util.Metrics;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    diagnostics().haltDiagnostics(LspPath.fromLspUri(params.getTextDocument().getUri()));
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
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> implementation(ImplementationParams params) {
    return new FindImplementationCommand(params.getPosition())
        .runAsync(session.getProject(), LspPath.fromLspUri(params.getTextDocument().getUri()));
  }

  @Override
  public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
    return new FindUsagesCommand(params.getPosition())
        .runAsync(session.getProject(), LspPath.fromLspUri(params.getTextDocument().getUri()));
  }

  @Override
  public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(DocumentHighlightParams params) {
    return new DocumentHighlightCommand(params.getPosition())
        .runAsync(session.getProject(), LspPath.fromLspUri(params.getTextDocument().getUri()));
  }

  @SuppressWarnings("deprecation")
  @Override
  public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params) {
    return new DocumentSymbolCommand().runAsync(session.getProject(), LspPath.fromLspUri(params.getTextDocument().getUri()));
  }

  @Override
  public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
    return CompletableFuture.completedFuture(
        codeActions().getCodeActions(
            LspPath.fromLspUri(params.getTextDocument().getUri()),
            params.getRange()
        ).stream().map((Function<CodeAction, Either<Command, CodeAction>>) Either::forRight).collect(Collectors.toList())
    );
  }


  @Override
  public CompletableFuture<CodeAction> resolveCodeAction(CodeAction unresolved) {
    return CompletableFuture.supplyAsync(() -> {
      var edit = codeActions().applyCodeAction(unresolved);
      unresolved.setEdit(edit);
      return unresolved;
    });
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
  private CodeActionService codeActions() {
    return session.getProject().getService(CodeActionService.class);
  }

  @NotNull
  private CompletionService completions() {
    return session.getProject().getService(CompletionService.class);
  }

  @NotNull
  private SignatureHelpService signature() {
    return session.getProject().getService(SignatureHelpService.class);
  }

  @Override
  @NotNull
  public CompletableFuture<CompletionItem> resolveCompletionItem(@NotNull CompletionItem unresolved) {
    return CompletableFutures.computeAsync(
        AppExecutorUtil.getAppExecutorService(),
        (cancelChecker) ->
            completions().resolveCompletion(unresolved, cancelChecker)
    );
  }

  @Override
  @NotNull
  public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(@NotNull CompletionParams params) {
    final var path = LspPath.fromLspUri(params.getTextDocument().getUri());
    return CompletableFutures.computeAsync(
        AppExecutorUtil.getAppExecutorService(),
        (cancelChecker) ->
            Either.forLeft(completions().computeCompletions(path, params.getPosition(), cancelChecker))
    );
  }

  @Override
  @NotNull
  public CompletableFuture<SignatureHelp> signatureHelp(SignatureHelpParams params) {
    final var path = LspPath.fromLspUri(params.getTextDocument().getUri());
    return CompletableFutures.computeAsync(AppExecutorUtil.getAppExecutorService(),
        cancelChecker -> signature().computeSignatureHelp(path, params.getPosition(), cancelChecker)
    );
  }


  @Override
  public CompletableFuture<List<? extends TextEdit>> formatting(@NotNull DocumentFormattingParams params) {
    return new FormattingCommand(null, params.getOptions())
        .runAsync(session.getProject(), LspPath.fromLspUri(params.getTextDocument().getUri()));
  }

  @Override
  public CompletableFuture<List<? extends TextEdit>> rangeFormatting(@NotNull DocumentRangeFormattingParams params) {
    return new FormattingCommand(params.getRange(), params.getOptions())
        .runAsync(session.getProject(), LspPath.fromLspUri(params.getTextDocument().getUri()));
  }

  @Override
  public CompletableFuture<List<? extends TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams params) {
    return new OnTypeFormattingCommand(params.getPosition(), params.getOptions(),
        params.getCh().charAt(0)).runAsync(
        session.getProject(), LspPath.fromLspUri(params.getTextDocument().getUri())
    );
  }

  @Override
  public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
    return new RenameCommand(params.getPosition(), params.getNewName())
        .runAsync(session.getProject(), LspPath.fromLspUri(params.getTextDocument().getUri()));
  }
}
