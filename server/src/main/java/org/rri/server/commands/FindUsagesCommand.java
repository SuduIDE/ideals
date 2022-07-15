package org.rri.server.commands;

import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Factory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.usages.*;
import com.intellij.util.Processor;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.server.util.MiscUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class FindUsagesCommand extends MyCommand<List<? extends Location>> {
    private final Position position;

    public FindUsagesCommand(Position position) {
        this.position = position;
    }

    @Override
    public List<? extends Location> apply(ExecutorContext ctx) {
        PsiFile file = ctx.getPsiFile();
        Document doc = MiscUtil.getDocument(file);
        if (doc == null) { return new ArrayList<>(); }
        int offset = MiscUtil.positionToOffset(position, doc);
        PsiElement element = ctx.getPsiFile().findElementAt(offset);

        var rawResults = findUsages(ctx.getProject(), element, ctx.getCancelToken());

        if (rawResults.isEmpty()) {
            return new ArrayList<>();
        }

        return rawResults.stream().filter(Objects::nonNull)
                .map(FindUsagesCommand::extractLocationFromRaw)
                .collect(Collectors.toList());
    }

    @Nullable
    private static Location extractLocationFromRaw(Usage usage) {
        if (usage instanceof UsageInfo2UsageAdapter) {
            var ui2ua = (UsageInfo2UsageAdapter) usage;
            var elem = ui2ua.getElement();
            if (elem == null) { return null; }
            return MiscUtil.psiElementLocation(ui2ua.getElement());
        }
        return null;
    }

    private static List<Usage> findUsages(Project project, PsiElement element, @Nullable CancelChecker cancelToken) {
        var rawResults = new ArrayList<Usage>();
//        var manager = new FindUsagesManager(project, new UsageCollectingViewManager(project, rawResults, cancelToken));
        var manager = new FindUsagesManager(project);
        TransactionGuard.getInstance().submitTransactionAndWait(() -> manager.findUsages(element, null, null, false, null));
        return rawResults;
    }

    private static class UsageCollectingViewManager extends UsageViewManager implements Processor<Usage> {
        private final Project project;
        private final List<Usage> results;
        private final CancelChecker cancelToken;

        public UsageCollectingViewManager(Project project, List<Usage> results, CancelChecker cancelToken) {
            this.project = project;
            this.results = results;
            this.cancelToken = cancelToken;
        }

        @Override
        public @NotNull UsageView showUsages(UsageTarget @NotNull [] searchedFor,
                                             Usage @NotNull [] foundUsages,
                                             @NotNull UsageViewPresentation presentation,
                                             @Nullable Factory<? extends UsageSearcher> factory) {
            return showUsages(searchedFor, foundUsages, presentation);
        }

        @Override
        public @NotNull UsageView showUsages(UsageTarget @NotNull [] searchedFor,
                                             Usage @NotNull [] foundUsages,
                                             @NotNull UsageViewPresentation presentation) {
            for (var usage : foundUsages) {
                process(usage);
            }
            return UsageViewManager.getInstance(project)
                    .createUsageView(UsageTarget.EMPTY_ARRAY, Usage.EMPTY_ARRAY, presentation, null);
        }

        @Override
        public @Nullable UsageView getSelectedUsageView() {
            return null;
        }

        @Override
        public @Nullable/*("returns null in case of no usages found or usage view not shown for one usage")*/
        UsageView searchAndShowUsages(UsageTarget @NotNull [] searchFor,
                                      @NotNull Factory<? extends UsageSearcher> searcherFactory,
                                      boolean showPanelIfOnlyOneUsage,
                                      boolean showNotFoundMessage,
                                      @NotNull UsageViewPresentation presentation,
                                      @Nullable UsageViewStateListener listener) {
            searcherFactory.create().generate(this);
            return null;
        }

        @Override
        public void searchAndShowUsages(UsageTarget @NotNull [] searchFor,
                                        @NotNull Factory<? extends UsageSearcher> searcherFactory,
                                        @NotNull FindUsagesProcessPresentation processPresentation,
                                        @NotNull UsageViewPresentation presentation,
                                        @Nullable UsageViewStateListener listener) {
            searcherFactory.create().generate(this);
        }

        @Override
        public @NotNull UsageView createUsageView(UsageTarget @NotNull [] targets,
                                                  Usage @NotNull [] usages,
                                                  @NotNull UsageViewPresentation presentation,
                                                  @Nullable Factory<? extends UsageSearcher> usageSearcherFactory) {
            return showUsages(targets, usages, presentation);
        }

        @Override
        public boolean process(Usage usage) {
            if (cancelToken != null) { cancelToken.checkCanceled(); }
            if (usage != null) { results.add(usage); }
            return true;
        }
    }
}
