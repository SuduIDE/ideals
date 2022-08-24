package org.rri.server.references;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.find.FindManager;
import com.intellij.find.findUsages.CustomUsageSearcher;
import com.intellij.find.findUsages.FindUsagesHandlerBase;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchRequestCollector;
import com.intellij.psi.search.SearchSession;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageInfoToUsageConverter;
import com.intellij.usages.UsageSearcher;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.server.commands.ExecutorContext;
import org.rri.server.commands.LspCommand;
import org.rri.server.util.EditorUtil;
import org.rri.server.util.MiscUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class FindUsagesCommand extends LspCommand<List<? extends Location>> {
  private static final Logger LOG = Logger.getInstance(FindUsagesCommand.class);
  @NotNull
  private final Position pos;

  public FindUsagesCommand(@NotNull Position pos) {
    this.pos = pos;
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
      return List.of();
    }
    var ref = new AtomicReference<PsiElement>();
    try {
      EditorUtil.withEditor(this, file, pos, editor -> {
        var targetElement = TargetElementUtil.findTargetElement(editor, TargetElementUtil.getInstance().getAllAccepted());
        ref.set(targetElement);
      });
    } finally {
      Disposer.dispose(this);
    }
    var target = ref.get();
    if (target == null) {
      return List.of();
    }
    return findUsages(ctx.getProject(), target, ctx.getCancelToken());
  }

  private static @NotNull List<@NotNull Location> findUsages(@NotNull Project project,
                                                             @NotNull PsiElement target,
                                                             @Nullable CancelChecker cancelToken) {
    var manager = ((FindManagerImpl) FindManager.getInstance(project)).getFindUsagesManager();
    var handler = manager.getFindUsagesHandler(target, FindUsagesHandlerFactory.OperationMode.USAGES_WITH_DEFAULT_OPTIONS);
    List<Location> result;
    if (handler != null) {
      var dialog = handler.getFindUsagesDialog(false, false, false);
      dialog.close(DialogWrapper.OK_EXIT_CODE);
      var options = dialog.calcFindUsagesOptions();
      PsiElement[] primaryElements = handler.getPrimaryElements();
      PsiElement[] secondaryElements = handler.getSecondaryElements();
      UsageSearcher searcher = createUsageSearcher(primaryElements, secondaryElements, handler, options, project);
      Set<Location> saver = ContainerUtil.newConcurrentSet();
      searcher.generate(usage -> {
        if (cancelToken != null) {
          try {
            cancelToken.checkCanceled();
          } catch (CancellationException e) {
            return false;
          }
        }
        if (usage instanceof final UsageInfo2UsageAdapter ui2ua && !ui2ua.isNonCodeUsage()) {
          var elem = ui2ua.getElement();
          var loc = MiscUtil.psiElementToLocation(elem);
          if (loc != null) {
            saver.add(loc);
          }
        }
        return true;
      });
      result = new ArrayList<>(saver);
    } else {
      result = ReferencesSearch.search(target).findAll().stream()
              .map(PsiReference::getElement)
              .map(MiscUtil::psiElementToLocation)
              .filter(Objects::nonNull)
              .collect(Collectors.toList());
    }
    return result;
  }

  // Took this function from com.intellij.find.findUsages.FindUsagesManager.
  // Reference solution (Ruin0x11/intellij-lsp-server) used outdated constructor of FindUsagesManager.
  // Now this constructor is not exists.
  @NotNull
  private static UsageSearcher createUsageSearcher(PsiElement @NotNull [] primaryElements,
                                                   PsiElement @NotNull [] secondaryElements,
                                                   @NotNull FindUsagesHandlerBase handler,
                                                   @NotNull FindUsagesOptions options,
                                                   @NotNull Project project) throws PsiInvalidElementAccessException {
    FindUsagesOptions optionsClone = options.clone();
    return processor -> {
      Processor<UsageInfo> usageInfoProcessor = new CommonProcessors.UniqueProcessor<>(usageInfo -> {
        Usage usage = usageInfo != null ? UsageInfoToUsageConverter.convert(primaryElements, usageInfo) : null;
        return processor.process(usage);
      });
      PsiElement[] elements = ArrayUtil.mergeArrays(primaryElements, secondaryElements, PsiElement.ARRAY_FACTORY);

      optionsClone.fastTrack = new SearchRequestCollector(new SearchSession(elements));
      if (optionsClone.searchScope instanceof GlobalSearchScope) {
        // we will search in project scope always but warn if some usage is out of scope
        optionsClone.searchScope = optionsClone.searchScope.union(GlobalSearchScope.projectScope(project));
      }
      try {
        for (PsiElement element : elements) {
          if (!handler.processElementUsages(element, usageInfoProcessor, optionsClone)) {
            return;
          }

          for (CustomUsageSearcher searcher : CustomUsageSearcher.EP_NAME.getExtensionList()) {
            try {
              searcher.processElementUsages(element, processor, optionsClone);
            } catch (ProcessCanceledException e) {
              throw e;
            } catch (Exception e) {
              LOG.error(e);
            }
          }
        }

        PsiSearchHelper.getInstance(project).processRequests(optionsClone.fastTrack, ref -> {
          UsageInfo info = ref.getElement().isValid() ? new UsageInfo(ref) : null;
          return usageInfoProcessor.process(info);
        });
      } finally {
        optionsClone.fastTrack = null;
      }
    };
  }
}
