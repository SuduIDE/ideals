package org.rri.ideals.server.diagnostics;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.util.MiscUtil;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service(Service.Level.PROJECT)
final public class DiagnosticsService {
  private static final Logger LOG = Logger.getInstance(DiagnosticsService.class);
  public static final int DELAY = 200;  // debounce delay ms -- massive updates one character each are typical when typing

  @NotNull
  private final Project project;

  private final ConcurrentHashMap<LspPath, FileDiagnosticsState> states = new ConcurrentHashMap<>();

  public DiagnosticsService(@NotNull Project project) {
    this.project = project;
  }

  public void launchDiagnostics(@NotNull LspPath path) {
    MiscUtil.invokeWithPsiFileInReadAction(project, path, (psiFile) -> {
      final var document = MiscUtil.getDocument(psiFile);
      if (document == null) {
        LOG.error("document not found: " + path);
        return;
      }

      Optional.ofNullable(states.put(path, launchDiagnostic(path, psiFile, document)))
          .ifPresent(FileDiagnosticsState::halt);
    });
  }

  public void haltDiagnostics(@NotNull LspPath path) {
    Optional.ofNullable(states.remove(path)).ifPresent(FileDiagnosticsState::halt);
  }

  @NotNull
  public List<HighlightInfo.IntentionActionDescriptor> getQuickFixes(@NotNull LspPath path, @NotNull Range range) {
    return Optional.ofNullable(states.get(path))
        .map(it -> it.getQuickFixes().collectForRange(range))
        .orElse(Collections.emptyList());
  }
  @NotNull
  private FileDiagnosticsState launchDiagnostic(@NotNull LspPath path,
                                                @NotNull PsiFile psiFile,
                                                @NotNull Document doc) {

    var quickFixes = new QuickFixRegistry();

    final var session = new DiagnosticsTask(path, psiFile, doc, new DiagnosticSession() {
      @Override
      public @NotNull QuickFixRegistry getQuickFixRegistry() {
        return quickFixes;
      }

      @Override
      public boolean isOutdated() {
        return quickFixes != Optional.ofNullable(states.get(path))
            .map(FileDiagnosticsState::getQuickFixes)
            .orElse(null);
      }

      @Override
      public void signalRestart() {
        launchDiagnostics(path);
      }
    });

    var task = AppExecutorUtil.getAppScheduledExecutorService().schedule(
        session, DELAY, TimeUnit.MILLISECONDS);

    return new FileDiagnosticsState(psiFile, quickFixes, task);

  }
}
