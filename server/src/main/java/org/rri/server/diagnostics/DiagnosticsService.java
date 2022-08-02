package org.rri.server.diagnostics;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.server.LspPath;
import org.rri.server.util.MiscUtil;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service(Service.Level.PROJECT)
final public class DiagnosticsService {
  private static final Logger LOG = Logger.getInstance(DiagnosticsService.class);
  public static final int DELAY = 200;  // debounce delay ms -- massive updates one character each are typical when typing

  private final ConcurrentHashMap<LspPath, DiagnosticsRecord> records = new ConcurrentHashMap<>();

  public void launchDiagnostics(@NotNull LspPath path) {
    Optional.ofNullable(records.get(path)).ifPresent(it -> it.quickFixes.dropQuickFixes());

    MiscUtil.withPsiFileInReadAction(project, path, (psiFile) -> {
      var doc = MiscUtil.getDocument(psiFile);

      if (doc == null) {
        LOG.warn("Unable to find Document for virtual file: " + path);
        return;
      }

      final var record = getDiagnosticsRecord(path);

      var current = record.task;

      if(current != null && current.getDelay(TimeUnit.NANOSECONDS) > 0) {
        LOG.debug("Cancelling not launched task for: " + path);
        current.cancel(true);
      }

      if(current == null || current.isDone() || current.isCancelled()) {
        LOG.debug("Scheduling delayed task for: " + path);
        record.task = launchDelayedTask(psiFile, doc, record.quickFixes);
      }
    });
  }

  @NotNull
  private final Project project;

  public DiagnosticsService(@NotNull Project project) {
    this.project = project;
  }

  public void haltDiagnostics(@NotNull LspPath path) {
    final var removed = records.remove(path);
    if(removed != null) {
      final ScheduledFuture<?> currentTask = removed.task;
      if(currentTask != null) {
        currentTask.cancel(true);
      }
    }
  }

  public List<CodeAction> getCodeActions(@NotNull LspPath path, @NotNull Range range) {
    return getDiagnosticsRecord(path).quickFixes.getQuickFixes(range, null)
        .stream()
        .map(it -> MiscUtil.with(new CodeAction(it.getAction().getText()), ca -> {
          ca.setKind(CodeActionKind.QuickFix);
        }))
        .collect(Collectors.toList());
  }

  @NotNull
  private DiagnosticsRecord getDiagnosticsRecord(@NotNull LspPath path) {
    return records.computeIfAbsent(path, __ -> new DiagnosticsRecord(new QuickFixRegistry()));
  }

  @NotNull
  private ScheduledFuture<?> launchDelayedTask(@NotNull PsiFile psiFile,
                                               @NotNull Document doc,
                                               @NotNull QuickFixRegistry quickFixes) {
    return AppExecutorUtil.getAppScheduledExecutorService().schedule(
            new DiagnosticsTask(psiFile, doc, quickFixes), DELAY, TimeUnit.MILLISECONDS);
  }

  private static final class DiagnosticsRecord {
    private final @NotNull QuickFixRegistry quickFixes;
    private @Nullable ScheduledFuture<?> task;

    private DiagnosticsRecord(@NotNull QuickFixRegistry quickFixes) {
      this.task = null;
      this.quickFixes = quickFixes;
    }
  }
}
