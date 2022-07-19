package org.rri.server.references;

import com.intellij.codeInsight.navigation.actions.GotoTypeDeclarationAction;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.rri.server.commands.ExecutorContext;
import org.rri.server.commands.LspCommand;
import org.rri.server.util.EditorUtil;
import org.rri.server.util.MiscUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class FindTypeDefinitionCommand extends LspCommand<Either<List<? extends Location>, List<? extends LocationLink>>> {
    @NonNull
    private final Position pos;

    public FindTypeDefinitionCommand(@NotNull Position pos) {
        this.pos = pos;
    }

    @Override
    public @NonNull Either<@NonNull List<@NonNull ? extends Location>, @NonNull List<@NonNull ? extends LocationLink>> apply(@NonNull ExecutorContext ctx) {
        PsiFile file = ctx.getPsiFile();
        Document doc = MiscUtil.getDocument(ctx.getPsiFile());
        if (doc == null) { return Either.forRight(new ArrayList<>()); }

        int offset = MiscUtil.positionToOffset(pos, doc);
        PsiElement originalElem = file.findElementAt(offset);
        Range originalRange = MiscUtil.getPsiElementRange(originalElem, doc);

        var ref = new AtomicReference<PsiElement[]>();
        EditorUtil.withEditor(this, file, MiscUtil.offsetToPosition(doc, offset), editor -> {
            var symbolTypes = GotoTypeDeclarationAction.findSymbolTypes(editor, offset);
            ref.set(symbolTypes);
        });
        var result = ref.get();
        if (result == null) { return Either.forRight(new ArrayList<>()); }

        var locLst = Arrays.stream(result)
                .map(targetElem -> {
                    Document targetDoc = targetElem.getContainingFile().equals(file)
                            ? doc : MiscUtil.getDocument(targetElem.getContainingFile());
                    return MiscUtil.psiElementToLocationLink(targetElem, targetDoc, originalRange);
                })
                .collect(Collectors.toList());

        return Either.forRight(locLst);
    }
}
