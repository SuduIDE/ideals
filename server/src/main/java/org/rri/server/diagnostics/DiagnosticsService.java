package org.rri.server.diagnostics;

import com.google.gson.GsonBuilder;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.jetbrains.annotations.NotNull;
import org.rri.server.LspPath;
import org.rri.server.util.EditorUtil;
import org.rri.server.util.MiscUtil;
import org.rri.server.util.TextUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service(Service.Level.PROJECT)
final public class DiagnosticsService {
  private static final Logger LOG = Logger.getInstance(DiagnosticsService.class);
  public static final int DELAY = 200;  // debounce delay ms -- massive updates one character each are typical when typing

  private final ConcurrentHashMap<LspPath, FileDiagnosticsState> states = new ConcurrentHashMap<>();

  public void launchDiagnostics(@NotNull LspPath path) {
    MiscUtil.withPsiFileInReadAction(project, path, (psiFile) -> {
      final var document = MiscUtil.getDocument(psiFile);
      if (document == null) {
        LOG.error("document not found: " + path);
        return;
      }

      var quickFixes = new QuickFixRegistry();

      DaemonCodeAnalyzer.getInstance(project).restart(psiFile);

      var task = launchDelayedTask(path, psiFile, document, quickFixes);

      Optional.ofNullable(states.put(path, new FileDiagnosticsState(quickFixes, task)))
          .ifPresent(FileDiagnosticsState::halt);
    });
  }

  @NotNull
  private final Project project;

  public DiagnosticsService(@NotNull Project project) {
    this.project = project;
  }

  public void haltDiagnostics(@NotNull LspPath path) {
    Optional.ofNullable(states.remove(path)).ifPresent(FileDiagnosticsState::halt);
  }

  @NotNull
  public List<CodeAction> getCodeActions(@NotNull LspPath path, @NotNull Range range) {
    return Optional.ofNullable(states.get(path))
        .map(state ->
            state.getQuickFixes().collectForRange(range)
                .stream()
                .map(it -> MiscUtil.with(new CodeAction(ReadAction.compute(() -> it.getAction().getText())), ca -> {
                  ca.setKind(CodeActionKind.QuickFix);
                  ca.setData(new ActionData(path.toLspUri(), range));
                }))
                .collect(Collectors.toList()))
        .orElse(Collections.emptyList());
  }

  @NotNull
  public WorkspaceEdit applyCodeAction(@NotNull CodeAction codeAction) {
    final var actionData = new GsonBuilder().create()
        .fromJson(codeAction.getData().toString(), ActionData.class);

    final var path = LspPath.fromLspUri(actionData.getUri());
    final var result = new WorkspaceEdit();

    var disposable = Disposer.newDisposable();

    try {
      final var diagnosticsState = states.get(path);

      if (diagnosticsState == null) {
        LOG.warn("No diagnostics state exists: " + path);
        return result;
      }

      final var actionDescriptor = ReadAction.compute(
          () -> diagnosticsState.getQuickFixes().collectForRange(actionData.getRange())
              .stream()
              .filter(it -> it.getAction().getText().equals(codeAction.getTitle()))
              .findFirst()
              .orElse(null)
      );

      if (actionDescriptor == null) {
        LOG.warn("No action descriptor found: " + codeAction.getTitle());
        return result;
      }

      final var psiFile = MiscUtil.resolvePsiFile(project, path);

      if (psiFile == null) {
        LOG.error("couldn't find PSI file: " + path);
        return result;
      }

      final var oldCopy = ((PsiFile) psiFile.copy());

      ApplicationManager.getApplication().invokeAndWait(() -> {
        final var editor = EditorUtil.createEditor(disposable, psiFile, actionData.getRange().getStart());

        final var action = actionDescriptor.getAction();
        CommandProcessor.getInstance().executeCommand(project, () -> {
          if (action.startInWriteAction()) {
            WriteAction.run(() -> action.invoke(project, editor, psiFile));
          } else {
            action.invoke(project, editor, psiFile);
          }
        }, codeAction.getTitle(), null);
      });

      final var oldDoc = new Ref<Document>();
      final var newDoc = new Ref<Document>();

      ReadAction.run(() -> {
        oldDoc.set(Objects.requireNonNull(MiscUtil.getDocument(oldCopy)));
        newDoc.set(Objects.requireNonNull(MiscUtil.getDocument(psiFile)));
      });

      final var edits = TextUtil.textEditFromDocs(oldDoc.get(), newDoc.get());

      WriteCommandAction.runWriteCommandAction(project, () -> {
        newDoc.get().setText(oldDoc.get().getText());
        PsiDocumentManager.getInstance(project).commitDocument(newDoc.get());
      });

      if (!edits.isEmpty()) {
        states.remove(path, diagnosticsState);  // all cached quick fixes are no longer valid
        result.setChanges(Map.of(actionData.getUri(), edits));
      }
    } finally {
      ApplicationManager.getApplication().invokeAndWait(() -> Disposer.dispose(disposable));
    }

    launchDiagnostics(path);
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

    @SuppressWarnings("unused") // used via reflection
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

}
