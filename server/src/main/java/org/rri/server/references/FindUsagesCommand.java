package org.rri.server.references;

import com.intellij.find.FindManager;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.server.commands.ExecutorContext;
import org.rri.server.commands.LspCommand;
import org.rri.server.util.MiscUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class FindUsagesCommand extends LspCommand<List<? extends Location>> {
    @NotNull
    private final Position position;

    public FindUsagesCommand(@NotNull Position position) {
      this.position = position;
    }

    @Override
    protected @NotNull Supplier<@NotNull String> getMessageSupplier() {
        return () -> "References (Find usages) call";
    }

    @Override
    protected boolean isCancellable() {
        return true;
    }

    @Override
    protected @NotNull List<? extends Location> execute(@NotNull ExecutorContext ctx) {
        PsiFile file = ctx.getPsiFile();
        Document doc = MiscUtil.getDocument(file);
        if (doc == null) {
            return new ArrayList<>();
        }
        int offset = MiscUtil.positionToOffset(position, doc);
        PsiElement target;
        PsiReference ref = file.findReferenceAt(offset);
        if (ref == null) {
            PsiElement elem = file.findElementAt(offset);
            if (elem == null) { return List.of(); }
            target = elem.getParent();
        } else {
            target = ref.resolve();
        }
        if (target == null) { return List.of(); }
        return findUsages(ctx.getProject(), target, ctx.getCancelToken());
    }

    private static @NotNull List<@NotNull Location> findUsages(@NotNull Project project,
                                                               @NotNull PsiElement target,
                                                               @Nullable CancelChecker cancelToken) {
        var manager = ((FindManagerImpl) FindManager.getInstance(project)).getFindUsagesManager();
        var handler = manager.getFindUsagesHandler(target, false);
        List<Location> result;
        if (handler != null) {
            var options = handler.getFindUsagesOptions();
            result = new ArrayList<>();
            handler.processElementUsages(target, usageInfo -> {
                if (cancelToken != null) {
                    cancelToken.checkCanceled();
                }
                if (usageInfo != null) {
                    var loc = MiscUtil.psiElementToLocation(usageInfo.getElement());
                    if (loc != null) {
                        result.add(loc);
                    }
                }
                return true;
            }, options);
        } else {
            result = ReferencesSearch.search(target).findAll().stream()
                    .map(PsiReference::getElement)
                    .map(MiscUtil::psiElementToLocation)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
        return result;
    }
}
