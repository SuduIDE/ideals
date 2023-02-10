package org.rri.ideals.server.symbol;

import com.intellij.openapi.diagnostic.Logger;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.commands.ExecutorContext;
import org.rri.ideals.server.commands.LspCommand;
import org.rri.ideals.server.util.MiscUtil;

import java.util.List;
import java.util.function.Supplier;

@SuppressWarnings("deprecation")
public class DocumentSymbolCommand extends LspCommand<List<Either<SymbolInformation, DocumentSymbol>>> {
  private static final Logger LOG = Logger.getInstance(DocumentSymbolCommand.class);

  @Override
  protected @NotNull Supplier<@NotNull String> getMessageSupplier() {
    return () -> "Document symbol call";
  }

  @Override
  protected boolean isCancellable() {
    return true;
  }

  @Override
  protected @NotNull List<Either<SymbolInformation, @NotNull DocumentSymbol>> execute(@NotNull ExecutorContext ctx) {
    final var document = MiscUtil.getDocument(ctx.getPsiFile());
    if (document == null) {
      LOG.error("No document found:" + LspPath.fromVirtualFile(ctx.getPsiFile().getVirtualFile()).toLspUri());
      return List.of();
    }
    final var visitor = new DocumentSymbolPsiVisitor(ctx.getPsiFile(), ctx.getCancelToken(), document);
    return visitor.visit().stream().map(Either::<SymbolInformation, DocumentSymbol>forRight).toList();
//    FileStructurePopup popup = ViewStructureAction.createPopup(ctx.getProject(), );
  }
}