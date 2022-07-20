package org.rri.server.references;

import com.intellij.codeInsight.navigation.actions.GotoTypeDeclarationAction;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.rri.server.commands.ExecutorContext;

import java.util.List;

public class FindTypeDefinitionCommand extends FindDefinitionCommand {
    public FindTypeDefinitionCommand(@NotNull Position pos) {
        super(pos);
    }

    @Override
    public @NotNull Either<@NotNull List<? extends Location>, @NotNull List<? extends LocationLink>> apply(@NotNull ExecutorContext ctx) {
        return getLocationLinks(ctx, GotoTypeDeclarationAction::findSymbolTypes);
    }
}
