package org.rri.server.commands;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.rri.server.LspPath;
import org.rri.server.util.MiscUtil;

import java.util.ArrayList;
import java.util.List;


public class FindDefinitionCommand extends MyCommand<Either<List<? extends Location>, List<? extends LocationLink>>> {
    private final Position pos;

    public FindDefinitionCommand(Position pos) {
        this.pos = pos;
    }

    @Override
    public Either<List<? extends Location>, List<? extends LocationLink>> apply(ExecutorContext ctx) {
        PsiFile file = ctx.getPsiFile();
        Document doc = MiscUtil.getDocument(file);
        if (doc == null) { return Either.forRight(new ArrayList<>()); }

        int offset = MiscUtil.positionToOffset(pos, doc);
        PsiElement originalElem = file.findElementAt(offset);
        Range originalRange = MiscUtil.psiElementRange(originalElem, doc);

        PsiReference ref = file.findReferenceAt(offset);
        if (ref == null) { return Either.forRight(new ArrayList<>()); }

        PsiElement targetElem = ref.resolve();
        if (targetElem == null) { return Either.forRight(new ArrayList<>()); }

        Range targetRange = MiscUtil.psiElementRange(targetElem, doc);
        String uri = LspPath.fromVirtualFile(targetElem.getContainingFile().getVirtualFile()).toLspUri();
        LocationLink loc = new LocationLink(uri, targetRange, targetRange, originalRange);
        List<LocationLink> lst = new ArrayList<>();
        lst.add(loc);
        return Either.forRight(lst);
    }
}
