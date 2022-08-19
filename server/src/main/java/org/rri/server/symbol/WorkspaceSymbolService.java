package org.rri.server.symbol;

import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereToggleAction;
import com.intellij.ide.actions.searcheverywhere.SymbolSearchEverywhereContributor;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.server.symbol.provider.DocumentSymbolInfoUtil;
import org.rri.server.util.MiscUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class WorkspaceSymbolService {
  @NotNull
  private final String pattern;

  private final int LIMIT = 100;

  public WorkspaceSymbolService(@NotNull String pattern) {
    this.pattern = pattern;
  }

  @SuppressWarnings("deprecation")
  public CompletableFuture<Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>> execute(@NotNull Project project) {
    return CompletableFuture.supplyAsync(() -> {
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
      final var ref = new Ref<List<WorkspaceSymbol>>();
      ApplicationManager.getApplication().runReadAction(
          () -> ref.set(search(contributorRef.get(), LIMIT, pattern)));
      return Either.forRight(ref.get());
    }, AppExecutorUtil.getAppExecutorService());
  }

  public List<WorkspaceSymbol> search(@NotNull SymbolSearchEverywhereContributor contributor,
                                      int limit,
                                      @NotNull String pattern) {
    if (pattern.isEmpty()) {
      return null;
    }

    final var res = new ArrayList<Pair<Object, Integer>>();
    try {
      var indicator = new DaemonProgressIndicator();
      ApplicationManager.getApplication().executeOnPooledThread(() ->
          contributor.fetchWeightedElements(pattern, indicator,
              descriptor -> {
                res.add(new Pair<>(descriptor.getItem(), descriptor.getWeight()));
                return res.size() < limit;
              })).get();
    } catch (InterruptedException | ExecutionException ignored) {
    }

    return res.stream()
        .map(p -> p.getFirst())
        .map(WorkspaceSymbolService::toWorkspaceSymbol)
        .filter(Objects::nonNull)
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
    final var containerName = elem.getParent() instanceof PsiNameIdentifierOwner
        ? ((PsiNameIdentifierOwner) elem).getName()
        : null;
    return new WorkspaceSymbol(info.getName(), info.getKind(), Either.forLeft(MiscUtil.psiElementToLocation(elem)), containerName);
  }
}
