package org.rri.server.diagnostics;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightingSessionImpl;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.services.LanguageClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.server.LspPath;
import org.rri.server.util.Metrics;
import org.rri.server.util.MiscUtil;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

class DiagnosticsTask implements Runnable {

  private final static Logger LOG = Logger.getInstance(DiagnosticsTask.class);

  @NotNull
  private final PsiFile file;
  @NotNull
  private final Document document;
  @NotNull
  private final LanguageClient client;

  public DiagnosticsTask(@NotNull PsiFile file, @NotNull Document document, @NotNull LanguageClient client) {
    this.file = file;
    this.document = document;
    this.client = client;
  }

  @Override
  public void run() {
    var diags = getHighlights(file, document).stream()
            .map((it) -> toDiagnostic(it, document))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    client.publishDiagnostics(new PublishDiagnosticsParams(LspPath.fromLspUri(file.getVirtualFile().getUrl()).toLspUri(), diags));
  }

  @NotNull
  private List<HighlightInfo> getHighlights(@NotNull PsiFile file, @NotNull Document doc) {
    var disposable = Disposer.newDisposable();
    try {
      return Metrics.call(
        () -> "Analyzing file: " + file.getVirtualFile(),
        () -> doHighlighting(disposable, doc, file)
      );
    }
    finally {
      Disposer.dispose(disposable);
    }
  }

  @NotNull
  private List<HighlightInfo> doHighlighting(@NotNull Disposable context,
                                             @NotNull Document doc, @NotNull PsiFile psiFile) {

    var progress = new DaemonProgressIndicator();

    Disposer.register(context, progress);

    var project = psiFile.getProject();

    return ProgressManager.getInstance().runProcess(() -> {
      var analyzer = DaemonCodeAnalyzerEx.getInstanceEx(project);

      // ensure we get fresh results; the restart also seems to
      //  prevent the "process canceled" issue (see #30)
      //PsiDocumentManager.getInstance(document).commitAllDocuments()

      return ReadAction.<List<HighlightInfo>>nonBlocking(() -> {
        try {
          analyzer.restart(psiFile);
          final var range = ProperTextRange.create(0, document.getTextLength());

          // this shouldn't be needed but for some reason the next call fails without it
          HighlightingSessionImpl.runInsideHighlightingSession(psiFile, progress, null, range, false, () -> {});

          final var result = analyzer.runMainPasses(psiFile, doc, progress);
          LOG.trace("Analyzing file: produced items: " + result.size());
          return result;
        } catch (IndexNotReadyException e) {
          LOG.warn("Analyzing file: index not ready");
          return Collections.emptyList();
        }
        catch (ProcessCanceledException e) {
          LOG.trace("Highlighting has been cancelled: " + file.getVirtualFile());
          throw e;
        }
      }).executeSynchronously();

    }, progress);
  }

  @Nullable
  private static Diagnostic toDiagnostic(@NotNull HighlightInfo info, @NotNull Document doc) {
    if (info.getDescription() == null)
      return null;

    var description = info.getDescription();
    var start = MiscUtil.offsetToPosition(doc, info.getStartOffset());
    var end = MiscUtil.offsetToPosition(doc, info.getEndOffset());

    return new Diagnostic(new Range(start, end), description, diagnosticSeverity(info.getSeverity()), "rriij");
  }

  @NotNull
  private static DiagnosticSeverity diagnosticSeverity(@NotNull HighlightSeverity severity) {
    if (severity == HighlightSeverity.INFORMATION)
      return DiagnosticSeverity.Information;
    if (severity == HighlightSeverity.WARNING)
      return DiagnosticSeverity.Warning;
    if (severity == HighlightSeverity.ERROR)
      return DiagnosticSeverity.Error;

    return DiagnosticSeverity.Hint;
  }
}
