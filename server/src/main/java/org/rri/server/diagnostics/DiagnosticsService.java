package org.rri.server.diagnostics;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.rri.server.LspPath;
import org.rri.server.util.MiscUtil;

import java.util.HashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service(Service.Level.PROJECT)
final public class DiagnosticsService {
  private static final Logger LOG = Logger.getInstance(DiagnosticsService.class);
  public static final int DELAY = 200;  // debounce delay ms -- massive updates one character each are typical when typing
  private final HashMap<LspPath, ScheduledFuture<?>> tasks = new HashMap<>();

  @NotNull
  private final Project project;

  public DiagnosticsService(@NotNull Project project) {
    this.project = project;
  }

  public void launchDiagnostics(@NotNull LspPath path) {
    MiscUtil.withPsiFileInReadAction(project, path, (psiFile) -> {
      var doc = MiscUtil.getDocument(psiFile);

      if (doc == null) {
        LOG.warn("Unable to find Document for virtual file: " + path);
        return;
      }

      synchronized (tasks) {
        var current = tasks.get(path);

        if(current != null && current.getDelay(TimeUnit.NANOSECONDS) > 0) {
          LOG.debug("Cancelling not launched task for: " + path);
          current.cancel(true);
        }

        if(current == null || current.isDone() || current.isCancelled()) {
          LOG.debug("Scheduling delayed task for: " + path);
          tasks.put(path, launchDelayedTask(psiFile, doc));
        }
      }
    });
  }

  @NotNull
  private ScheduledFuture<?> launchDelayedTask(@NotNull PsiFile psiFile, @NotNull Document doc) {
    return AppExecutorUtil.getAppScheduledExecutorService().schedule(
            new DiagnosticsTask(psiFile, doc), DELAY, TimeUnit.MILLISECONDS);
  }
}
