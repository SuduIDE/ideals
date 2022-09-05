package org.rri.ideals.server.references;

import com.intellij.codeInsight.navigation.actions.GotoTypeDeclarationAction;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.commands.ExecutorContext;

import java.util.List;
import java.util.function.Supplier;

public class FindTypeDefinitionCommand extends FindDefinitionCommand {
  public FindTypeDefinitionCommand(@NotNull Position pos) {
    super(pos);
  }

  @Override
  protected @NotNull Supplier<@NotNull String> getMessageSupplier() {
    return () -> "TypeDefinition call";
  }

  @Override
  protected @NotNull Either<@NotNull List<? extends Location>, @NotNull List<? extends LocationLink>> execute(@NotNull ExecutorContext ctx) {
    return getLocationLinks(ctx, GotoTypeDeclarationAction::findSymbolTypes);
  }
}
