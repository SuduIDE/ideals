package org.rri.server.util;

import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.fragments.DiffFragment;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.DumbProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class TextUtil {
  private TextUtil() {
  }

  @NotNull
  public static TextRange toTextRange(@NotNull Document doc, Range range) {
    return new TextRange(
        MiscUtil.positionToOffset(doc, range.getStart()),
        MiscUtil.positionToOffset(doc, range.getEnd())
    );
  }

  @NotNull
  public static List<@NotNull TextEdit> differenceAfterAction(@NotNull PsiFile psiFile,
                                                              @NotNull Consumer<@NotNull PsiFile> action) {
    var copy = PsiFileFactory.getInstance(psiFile.getProject()).createFileFromText(
        "copy",
        psiFile.getLanguage(),
        psiFile.getText(),
        true,
        true,
        true,
        psiFile.getVirtualFile());

    action.accept(copy);

    var oldDoc = MiscUtil.getDocument(psiFile);
    assert oldDoc != null;
    var newDoc = MiscUtil.getDocument(copy);
    assert newDoc != null;
    return textEditFromDocs(oldDoc, newDoc);
  }

  @NotNull
  private static List<@NotNull DiffFragment> diff(@NotNull String oldText, @NotNull String newText) {
    var indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator == null) {
      indicator = DumbProgressIndicator.INSTANCE;
    }

    return ComparisonManager.getInstance().compareChars(oldText, newText, ComparisonPolicy.DEFAULT, indicator);
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
}
