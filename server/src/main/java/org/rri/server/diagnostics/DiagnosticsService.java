package org.rri.server.diagnostics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.server.LspPath;
import org.rri.server.util.EditorUtil;
import org.rri.server.util.MiscUtil;
import org.rri.server.util.TextUtil;

import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    Optional.ofNullable(records.get(path)).ifPresent(DiagnosticsRecord::clearDiagnostics);

    MiscUtil.withPsiFileInReadAction(project, path, (psiFile) -> {
      var fileCopyToDiagnose = PsiFileFactory.getInstance(psiFile.getProject()).createFileFromText(
          "copy",
          psiFile.getLanguage(),
          MiscUtil.getDocument(psiFile).getText(),
          true,
          true,
          true,
          psiFile.getVirtualFile());

      var doc = MiscUtil.getDocument(fileCopyToDiagnose);

      if (doc == null) {
        LOG.warn("Unable to find Document for virtual file: " + path);
        return;
      }

      final var record = getDiagnosticsRecord(path);
      record.fileCopyToDiagnose = fileCopyToDiagnose;

      var current = record.task;

      if (current != null && current.getDelay(TimeUnit.NANOSECONDS) > 0) {
        LOG.debug("Cancelling not launched task for: " + path);
        current.cancel(true);
      }

      if (current == null || current.isDone() || current.isCancelled()) {
        LOG.debug("Scheduling delayed task for: " + path);
        record.task = launchDelayedTask(path, fileCopyToDiagnose, doc, record.quickFixes);
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
    if (removed != null) {
      final ScheduledFuture<?> currentTask = removed.task;
      if (currentTask != null) {
        currentTask.cancel(true);
      }
    }
  }

  @NotNull
  public List<CodeAction> getCodeActions(@NotNull LspPath path, @NotNull Range range) {
    return getDiagnosticsRecord(path).quickFixes.getQuickFixes(range, null)
        .stream()
        .map(it -> MiscUtil.with(new CodeAction(ReadAction.compute(() -> it.getAction().getText())),ca -> {
          ca.setKind(CodeActionKind.QuickFix);
          ca.setData(new ActionData(path.toLspUri(), range));
        }))
        .collect(Collectors.toList());
  }

  ;

  @NotNull
  public WorkspaceEdit applyCodeAction(@NotNull CodeAction codeAction) {
    Gson gson = new GsonBuilder().create();
    var actionData = gson.fromJson(codeAction.getData().toString(), ActionData.class);

    var path = LspPath.fromLspUri(actionData.getUri());

    final var result = new WorkspaceEdit();

    var disposable = Disposer.newDisposable();

    try {
      final var diagnosticsRecord = getDiagnosticsRecord(path);
      final var actionDescriptor = ReadAction.compute( () -> {
        return diagnosticsRecord.quickFixes.getQuickFixes(actionData.getRange(), null)
            .stream()
            .filter(it -> it.getAction().getText().equals(codeAction.getTitle()))
            .findFirst()
            .orElse(null);
      });

      if(actionDescriptor == null) {
        LOG.warn("No action descriptor found: " + codeAction.getTitle());
        return result;
      }

      final var psiFile = diagnosticsRecord.fileCopyToDiagnose;

      if(psiFile == null) {
        LOG.warn("No PSI file associated with quick fixes: " + path);
        return result;
      }

      final var oldCopy = ((PsiFile) psiFile.copy());

      var manager = PsiDocumentManager.getInstance(project);
      var doc = MiscUtil.getDocument(psiFile);
      assert doc != null;
      assert  manager.isCommitted(doc);

      Ref<Editor> editorHolder = new Ref<>();

      ApplicationManager.getApplication().invokeAndWait(() -> {
        editorHolder.set(EditorUtil.createEditor(disposable, psiFile, actionData.getRange().getStart()));

        final var action = actionDescriptor.getAction();
        CommandProcessor
            .getInstance()
            .executeCommand( project, () -> {
              if (action.startInWriteAction()) {
                WriteAction.run(() -> {
                  action.invoke(project, editorHolder.get(), psiFile);
                });
              } else {
                action.invoke(project, editorHolder.get(), psiFile);
              }
            }, codeAction.getTitle(), null);
      });

      ReadAction.run(() -> {
        var oldDoc = MiscUtil.getDocument(oldCopy);
        assert oldDoc != null;
        var newDoc = MiscUtil.getDocument(psiFile);
        assert newDoc != null;

        final var edits = TextUtil.textEditFromDocs(oldDoc, newDoc);

        if(!edits.isEmpty()) {
          diagnosticsRecord.clearDiagnostics();
        }

        result.setChanges(Map.of(actionData.getUri(), edits));
      });

    } finally {
      ApplicationManager.getApplication().invokeAndWait( () -> Disposer.dispose(disposable));
    }

    return result;
  }

  @NotNull
  private ScheduledFuture<?> launchDelayedTask(@NotNull LspPath path,
                                               @NotNull PsiFile psiFile,
                                               @NotNull Document doc,
                                               @NotNull QuickFixRegistry quickFixes) {
    return AppExecutorUtil.getAppScheduledExecutorService().schedule(
        new DiagnosticsTask(path, psiFile, doc, quickFixes), DELAY, TimeUnit.MILLISECONDS);
  }

  @NotNull
  private DiagnosticsRecord getDiagnosticsRecord(@NotNull LspPath path) {
    return records.computeIfAbsent(path, __ -> new DiagnosticsRecord(new QuickFixRegistry()));
  }

  private static final class ActionData {
    private String uri;
    private Range range;

    private ActionData(String uri, Range range) {
      this.uri = uri;
      this.range = range;
    }

    public String getUri() {
      return uri;
    }

    public void setUri(String uri) {
      this.uri = uri;
    }

    public Range getRange() {
      return range;
    }

    public void setRange(Range range) {
      this.range = range;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) return true;
      if (obj == null || obj.getClass() != this.getClass()) return false;
      var that = (ActionData) obj;
      return Objects.equals(this.uri, that.uri) &&
          Objects.equals(this.range, that.range);
    }

    @Override
    public int hashCode() {
      return Objects.hash(uri, range);
    }

    @Override
    public String toString() {
      return "ActionData[" +
          "uri=" + uri + ", " +
          "range=" + range + ']';
    }
  }

  private static final class DiagnosticsRecord {
    private final @NotNull QuickFixRegistry quickFixes;

    private @Nullable PsiFile fileCopyToDiagnose;
    private @Nullable ScheduledFuture<?> task;

    private DiagnosticsRecord(@NotNull QuickFixRegistry quickFixes) {
      this.quickFixes = quickFixes;
    }

    void clearDiagnostics() {
      quickFixes.dropQuickFixes();
      fileCopyToDiagnose = null;
    }
  }
}
