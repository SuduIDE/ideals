package org.rri.server.symbol;

import com.esotericsoftware.minlog.Log;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.rri.server.commands.ExecutorContext;
import org.rri.server.commands.LspCommand;
import org.rri.server.util.MiscUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
  protected List<Either<SymbolInformation, DocumentSymbol>> execute(@NotNull ExecutorContext ctx) {
    final var document = MiscUtil.getDocument(ctx.getPsiFile());
    if (document == null) {
      // TODO need to throw exception
      Log.error("No document found.");
      return List.of();
    }
    final var symbols = new ArrayList<DocumentSymbol>();
    new DocumentSymbolPsiVisitor(ctx.getPsiFile(), ctx.getCancelToken(), elem -> {
      final var kind = TypeUtil.symbolKind(elem);
      final var name = TypeUtil.symbolName(elem);
      if (kind != null && name != null) {
        final var range = MiscUtil.getPsiElementRange(document, elem);
        symbols.add(new DocumentSymbol(name, kind, range, range));
      }
    }).visit();
    symbols.sort(Comparator.comparingInt(docSym -> MiscUtil.positionToOffset(document, docSym.getRange().getStart())));
    return symbols.stream().map(Either::<SymbolInformation, DocumentSymbol>forRight).collect(Collectors.toList());
  }
}
