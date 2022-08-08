package org.rri.server.symbol;

import com.esotericsoftware.minlog.Log;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.rri.server.commands.ExecutorContext;
import org.rri.server.commands.LspCommand;
import org.rri.server.util.MiscUtil;

import java.util.List;
import java.util.function.Supplier;

@SuppressWarnings("deprecation")
public class DocumentSymbolCommand extends LspCommand<List<Either<SymbolInformation, DocumentSymbol>>> {
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
      Log.error("No document found.");
      return List.of();
    }
    final var visitor = new DocumentSymbolPsiVisitor(ctx.getPsiFile(), ctx.getCancelToken(), document);
    return visitor.visit().stream().map(Either::<SymbolInformation, DocumentSymbol>forRight).toList();
  }
}