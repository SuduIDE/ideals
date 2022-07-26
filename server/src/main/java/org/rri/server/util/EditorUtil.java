package org.rri.server.util;

import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.fragments.DiffFragment;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.progress.DumbProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.codehaus.plexus.util.ExceptionUtils;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class EditorUtil {
  private static final Logger LOG = Logger.getInstance(EditorUtil.class);

  private EditorUtil() { }

  @NotNull
  public static Editor createEditor(@NotNull Disposable context,
                                    @NotNull PsiFile file,
                                    @NotNull Position position) {
    Document doc = MiscUtil.getDocument(file);
    EditorFactory editorFactory = EditorFactory.getInstance();

    assert doc != null;
    Editor created = editorFactory.createEditor(doc, file.getProject());
    created.getCaretModel().moveToLogicalPosition(new LogicalPosition(position.getLine(), position.getCharacter()));

    Disposer.register(context, () -> editorFactory.releaseEditor(created));

    return created;
  }


  public static void withEditor(@NotNull Disposable context,
                                @NotNull PsiFile file,
                                @NotNull Position position,
                                @NotNull Consumer<Editor> callback) {
    Editor editor = createEditor(context, file, position);

    try {
      callback.accept(editor);
    } catch (Exception e) {
      LOG.error("Exception during editor callback: " + e
              + ExceptionUtils.getStackTrace(e));
    }
  }

  @NotNull
  public static List<@NotNull TextEdit> differenceAfterAction(@NotNull PsiFile psiFile, @NotNull Consumer<@NotNull PsiFile> action) {
    var copy = (PsiFile) psiFile.copy();

    action.accept(copy);

    var oldDoc = MiscUtil.getDocument(psiFile);
    assert oldDoc != null;
    var newDoc = MiscUtil.getDocument(copy);
    assert newDoc != null;
    return textEditFromDocs(oldDoc, newDoc);
  }

  @NotNull
  private static List<@NotNull TextEdit> textEditFromDocs(@NotNull Document oldDoc, @NotNull Document newDoc) {
    var changes = diff(oldDoc.getText(), newDoc.getText());
    return changes.stream().map(diffFragment -> {
      var start = MiscUtil.offsetToPosition(oldDoc, diffFragment.getStartOffset1());
      var end = MiscUtil.offsetToPosition(oldDoc, diffFragment.getEndOffset1());
      var text = newDoc.getText(new TextRange(diffFragment.getStartOffset2(), diffFragment.getEndOffset2()));
      return new TextEdit(new Range(start, end), text);
    }).collect(Collectors.toList());
  }

  @NotNull
  private static List<@NotNull DiffFragment> diff(@NotNull String oldText, @NotNull String newText) {
    var indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator == null) {
      indicator = DumbProgressIndicator.INSTANCE;
    }

    return ComparisonManager.getInstance().compareChars(oldText, newText, ComparisonPolicy.DEFAULT, indicator);
  }
}
