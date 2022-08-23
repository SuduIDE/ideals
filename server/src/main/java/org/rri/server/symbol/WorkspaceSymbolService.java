package org.rri.server.symbol;

import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereToggleAction;
import com.intellij.ide.actions.searcheverywhere.SymbolSearchEverywhereContributor;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolLocation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.server.LspPath;
import org.rri.server.symbol.provider.DocumentSymbolInfoUtil;
import org.rri.server.util.MiscUtil;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Service(Service.Level.PROJECT)
final public class WorkspaceSymbolService {
  @NotNull
  private final Project project;
  @NotNull
  private final Map<@NotNull WorkspaceSymbol, @NotNull PsiElement> elements = new LinkedHashMap<>();

  private final int LIMIT = 100;

  public WorkspaceSymbolService(@NotNull Project project) {
    this.project = project;
  }

  @SuppressWarnings("deprecation")
  public @NotNull CompletableFuture<@NotNull Either<List<? extends SymbolInformation>, @Nullable List<? extends WorkspaceSymbol>>> runSearch(String pattern) {
    return CompletableFuture.supplyAsync(() -> {
      if (DumbService.isDumb(project)) {
        return Either.forRight(null);
      }
      final var result = execute(pattern);
      result.forEach(searchResult -> elements.put(searchResult.symbol(), searchResult.element()));
      final var lst = result.stream()
          .map(WorkspaceSearchResult::symbol)
          .toList();
      return Either.forRight(lst);
    }, AppExecutorUtil.getAppExecutorService());
  }

  public @NotNull CompletableFuture<@NotNull WorkspaceSymbol> resolveWorkspaceSymbol(@NotNull WorkspaceSymbol workspaceSymbol) {
    return CompletableFuture.supplyAsync(() -> {
      workspaceSymbol.setLocation(Either.forLeft(MiscUtil.psiElementToLocation(elements.get(workspaceSymbol))));
      elements.clear();
      return workspaceSymbol;
    });
  }

  private @NotNull List<@NotNull WorkspaceSearchResult> execute(@NotNull String pattern) {
    Ref<SymbolSearchEverywhereContributor> contributorRef = new Ref<>();
    ApplicationManager.getApplication().invokeAndWait(
        () -> {
          final var context = SimpleDataContext.getProjectContext(project);
          final var event = AnActionEvent.createFromDataContext("keyboard shortcut", null, context);
          final var contributor = new SymbolSearchEverywhereContributor(event);
          final var actions = contributor.getActions(() -> {
          });
          final var everywhereAction = (SearchEverywhereToggleAction) ContainerUtil.find(actions, o -> o instanceof SearchEverywhereToggleAction);
          everywhereAction.setEverywhere(true);
          contributorRef.set(contributor);
        }
    );
    final var ref = new Ref<List<WorkspaceSearchResult>>();
    ApplicationManager.getApplication().runReadAction(
        () -> ref.set(search(contributorRef.get(), pattern)));
    return ref.get();
  }

  private record WorkspaceSearchResult(WorkspaceSymbol symbol, PsiElement element) {
  }

  public @NotNull List<@NotNull WorkspaceSearchResult> search(
      @NotNull SymbolSearchEverywhereContributor contributor,
      @NotNull String pattern) {
    if (pattern.isEmpty()) {
      return List.of();
    }

    final var res = new ArrayList<Pair<WorkspaceSearchResult, Integer>>(LIMIT);
    try {
      final var indicator = new DaemonProgressIndicator();
      final var elements = new HashSet<PsiElement>();
      ApplicationManager.getApplication().executeOnPooledThread(() ->
          contributor.fetchWeightedElements(pattern, indicator,
              descriptor -> {
                assert descriptor.getItem() instanceof PsiElement;
                final var elem = (PsiElement) descriptor.getItem();
                if (elements.contains(elem)) {
                  return true;
                }
                final var workspaceSym = toWorkspaceSymbol(descriptor.getItem());
                if (workspaceSym == null) {
                  return true;
                }
                elements.add(elem);
                res.add(new Pair<>(new WorkspaceSearchResult(workspaceSym, (PsiElement) descriptor.getItem()),
                    descriptor.getWeight()));
                return res.size() < LIMIT;
              })).get();
    } catch (InterruptedException | ExecutionException ignored) {
    }

    return res.stream()
        .sorted(Comparator.<Pair<WorkspaceSearchResult, Integer>>comparingInt(p -> p.getSecond()).reversed())
        .map(p -> p.getFirst())
        .distinct()
        .toList();
  }

  private static @Nullable WorkspaceSymbol toWorkspaceSymbol(@NotNull Object o) {
    if (!(o instanceof final PsiElement elem)) {
      return null;
    }
    final var provider = DocumentSymbolInfoUtil.getDocumentSymbolProvider(elem.getLanguage());
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
    final var containerName = elem.getParent() instanceof PsiNameIdentifierOwner
        ? ((PsiNameIdentifierOwner) elem.getParent()).getName()
        : null;
    return new WorkspaceSymbol(info.getName(), info.getKind(),
        Either.forRight(new WorkspaceSymbolLocation(
            LspPath.fromVirtualFile(psiFile.getVirtualFile()).toLspUri())
        ), containerName);
  }
}
