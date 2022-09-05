package org.rri.ideals.server.symbol;

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereToggleAction;
import com.intellij.ide.actions.searcheverywhere.SymbolSearchEverywhereContributor;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.StandardProgressIndicator;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolTag;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.util.MiscUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

@Service(Service.Level.PROJECT)
final public class WorkspaceSymbolService {
  @NotNull
  private final Project project;

  private final int LIMIT = 100;

  private static final Comparator<WorkspaceSearchResult> COMP = Comparator.comparingInt(WorkspaceSearchResult::weight).reversed();

  public WorkspaceSymbolService(@NotNull Project project) {
    this.project = project;
  }

  @SuppressWarnings("deprecation")
  public @NotNull CompletableFuture<@NotNull Either<List<? extends SymbolInformation>, @Nullable List<? extends WorkspaceSymbol>>> runSearch(String pattern) {
    return CompletableFutures.computeAsync(AppExecutorUtil.getAppExecutorService(),
        cancelToken -> {
          if (DumbService.isDumb(project)) {
            return Either.forRight(null);
          }
          final var result = execute(pattern, cancelToken).stream()
              .map(WorkspaceSearchResult::symbol)
              .toList();
          return Either.forRight(result);
        });
  }

  private @NotNull List<@NotNull WorkspaceSearchResult> execute(@NotNull String pattern, @Nullable CancelChecker cancelToken) {
    Ref<SymbolSearchEverywhereContributor> contributorRef = new Ref<>();
    ApplicationManager.getApplication().invokeAndWait(
        () -> {
          final var context = SimpleDataContext.getProjectContext(project);
          final var event = AnActionEvent.createFromDataContext("keyboard shortcut", null, context);
          final var contributor = new SymbolSearchEverywhereContributor(event);
          if (!pattern.isEmpty()) {
            final var actions = contributor.getActions(() -> {
            });
            final var everywhereAction = (SearchEverywhereToggleAction) ContainerUtil.find(actions, o -> o instanceof SearchEverywhereToggleAction);
            everywhereAction.setEverywhere(true);
          }
          contributorRef.set(contributor);
        }
    );
    final var ref = new Ref<List<WorkspaceSearchResult>>();
    ApplicationManager.getApplication().runReadAction(
        () -> ref.set(search(contributorRef.get(), pattern.isEmpty() ? "*" : pattern, cancelToken)));
    return ref.get();
  }

  private record WorkspaceSearchResult(@NotNull WorkspaceSymbol symbol,
                                       @NotNull PsiElement element,
                                       int weight,
                                       boolean isProjectFile) {
  }

  // Returns: the list of founded symbols
  // Note: Project symbols first then symbols from libraries, jdks, environments...
  public @NotNull List<@NotNull WorkspaceSearchResult> search(
      @NotNull SymbolSearchEverywhereContributor contributor,
      @NotNull String pattern,
      @Nullable CancelChecker cancelToken) {
    final var projectSymbols = new ArrayList<WorkspaceSearchResult>(LIMIT);
    final var otherSymbols = new ArrayList<WorkspaceSearchResult>(LIMIT);
    final var scope = ProjectScope.getProjectScope(project);
    try {
      final var indicator = new WorkspaceSymbolIndicator(cancelToken);
      final var elements = new HashSet<PsiElement>();
      ApplicationManager.getApplication().executeOnPooledThread(() ->
          contributor.fetchWeightedElements(pattern, indicator,
              descriptor -> {
                if (!(descriptor.getItem() instanceof final PsiElement elem)
                    || elements.contains(elem)) {
                  return true;
                }
                final var searchResult = toSearchResult(descriptor, scope);
                if (searchResult == null) {
                  return true;
                }
                elements.add(elem);
                (searchResult.isProjectFile() ? projectSymbols : otherSymbols).add(searchResult);
                return projectSymbols.size() + otherSymbols.size() < LIMIT;
              })).get();
    } catch (InterruptedException | ExecutionException ignored) {
    }
    projectSymbols.sort(COMP);
    otherSymbols.sort(COMP);
    return Stream.of(projectSymbols, otherSymbols).flatMap(List::stream).toList();
  }

  private static @Nullable WorkspaceSearchResult toSearchResult(@NotNull FoundItemDescriptor<@NotNull Object> descriptor,
                                                                @NotNull SearchScope scope) {
    if (!(descriptor.getItem() instanceof final PsiElement elem)) {
      return null;
    }
    final var provider = DocumentSymbolInfoProvider.findFor(elem.getLanguage());
    if (provider == null) {
      return null;
    }
    final var info = provider.calculateSymbolInfo(elem);
    if (info == null) {
      return null;
    }
    final var psiFile = elem.getContainingFile();
    if (psiFile == null) {
      return null;
    }
    final var virtualFile = psiFile.getVirtualFile();
    final var containerName = elem.getParent() instanceof PsiNameIdentifierOwner
        ? ((PsiNameIdentifierOwner) elem.getParent()).getName()
        : null;
    final var location = new Location();
    location.setUri(LspPath.fromVirtualFile(virtualFile).toLspUri());
    final var symbol = new WorkspaceSymbol(info.getName(), info.getKind(),
        Either.forLeft(MiscUtil.psiElementToLocation(elem, psiFile)),
        containerName);
    if (provider.isDeprecated(elem)) {
      symbol.setTags(List.of(SymbolTag.Deprecated));
    }
    return new WorkspaceSearchResult(symbol, elem, descriptor.getWeight(), scope.contains(virtualFile));
  }

  private static class WorkspaceSymbolIndicator extends AbstractProgressIndicatorBase implements StandardProgressIndicator {
    @Nullable
    private final CancelChecker cancelToken;

    public WorkspaceSymbolIndicator(@Nullable CancelChecker cancelToken) {
      this.cancelToken = cancelToken;
    }

    @Override
    public void checkCanceled() {
      if (cancelToken != null) {
        try {
          cancelToken.checkCanceled();
        } catch (CancellationException e) {
          throw new ProcessCanceledException(e);
        }
      }
      super.checkCanceled();
    }
  }
}
