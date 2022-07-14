package org.rri.server.commands;

import com.intellij.codeInsight.navigation.actions.GotoTypeDeclarationAction;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.rri.server.LspPath;
import org.rri.server.util.EditorUtil;
import org.rri.server.util.MiscUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class FindTypeDefinitionCommand extends MyCommand<Either<List<? extends Location>, List<? extends LocationLink>>> {
    private final Position pos;

    public FindTypeDefinitionCommand(Position pos) {
        this.pos = pos;
    }

    @Override
    public Either<List<? extends Location>, List<? extends LocationLink>> apply(ExecutorContext ctx) {
        PsiFile file = ctx.getPsiFile();
        Document doc = MiscUtil.getDocument(ctx.getPsiFile());
        if (doc == null) { return Either.forRight(new ArrayList<>()); }

        int offset = MiscUtil.positionToOffset(pos, doc);
        PsiElement originalElem = file.findElementAt(offset);
        Range originalRange = MiscUtil.psiElementRange(originalElem, doc);

        var ref = new AtomicReference<PsiElement[]>();
        EditorUtil.withEditor(this, file, offset, (editor) -> {
            var symbolTypes = GotoTypeDeclarationAction.findSymbolTypes(editor, offset);
            ref.set(symbolTypes);
        });
        var result = ref.get();

        var locLst = Arrays.stream(result)
                .map(elem -> {
                    var uri = LspPath.fromVirtualFile(elem.getContainingFile().getVirtualFile()).toLspUri();
                    return MiscUtil.psiElementLocationWithOrig(elem, uri, doc, originalRange);
                })
                .collect(Collectors.toList());

        return Either.forRight(locLst);
    }
}
