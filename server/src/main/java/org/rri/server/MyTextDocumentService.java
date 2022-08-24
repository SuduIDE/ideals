package org.rri.server;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.jetbrains.annotations.NotNull;
import org.rri.server.completions.CompletionService;
import org.rri.server.diagnostics.DiagnosticsService;
import org.rri.server.formatting.FormattingCommand;
import org.rri.server.formatting.OnTypeFormattingCommand;
import org.rri.server.references.DocumentHighlightCommand;
import org.rri.server.references.FindDefinitionCommand;
import org.rri.server.references.FindTypeDefinitionCommand;
import org.rri.server.references.FindUsagesCommand;
import org.rri.server.symbol.DocumentSymbolCommand;
import org.rri.server.util.Metrics;

import java.util.ArrayList;
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
        diagnostics().getCodeActions(
            LspPath.fromLspUri(params.getTextDocument().getUri()),
            params.getRange()
        ).stream().map((Function<CodeAction, Either<Command, CodeAction>>) Either::forRight).collect(Collectors.toList())
    );
  }


  @Override
  public CompletableFuture<CodeAction> resolveCodeAction(CodeAction unresolved) {
    return CompletableFuture.supplyAsync(() -> {
      var edit = diagnostics().applyCodeAction(unresolved);
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
  private CompletionService completion() {
    return session.getProject().getService(CompletionService.class);
  }

  @Override
  @NotNull
  public CompletableFuture<CompletionItem> resolveCompletionItem(@NotNull CompletionItem unresolved) {
    return completion().startCompletionResolve(unresolved);
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
    return completion().startCompletionCalculation(path, params.getPosition());
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


}
