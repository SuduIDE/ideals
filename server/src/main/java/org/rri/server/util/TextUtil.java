package org.rri.server.util;

import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.fragments.DiffFragment;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.DumbProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
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
    var copy = getCopyByFileText(psiFile);
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
  public static List<@NotNull TextEdit> textEditFromDocs(@NotNull Document oldDoc, @NotNull Document newDoc) {
    var changes = diff(oldDoc.getText(), newDoc.getText());
    return changes.stream().map(diffFragment -> {
      var start = MiscUtil.offsetToPosition(oldDoc, diffFragment.getStartOffset1());
      var end = MiscUtil.offsetToPosition(oldDoc, diffFragment.getEndOffset1());
      var text = newDoc.getText(new TextRange(diffFragment.getStartOffset2(), diffFragment.getEndOffset2()));
      return new TextEdit(new Range(start, end), text);
    }).collect(Collectors.toList());
  }

  public static PsiFile getCopyByFileText(PsiFile psiFile) {
    var manager = PsiDocumentManager.getInstance(psiFile.getProject());
    var doc = MiscUtil.getDocument(psiFile);
    assert doc != null;
    assert  manager.isCommitted(doc);
    return PsiFileFactory.getInstance(psiFile.getProject()).createFileFromText(
        "copy",
        psiFile.getLanguage(),
        doc.getText(),
        true,
        true,
        true,
        psiFile.getVirtualFile());
  }

  public static class TextEditWithOffsets implements Comparable<TextEditWithOffsets> {
    private final Pair<Integer, Integer> range;
    private String newText;

    public TextEditWithOffsets(Integer start, Integer end, String newText) {
      this.range = new Pair<>(start, end);
      this.newText = newText;
    }

    @Override
    public int compareTo(@NotNull TextEditWithOffsets otherTextEditWithOffsets) {
      int res = this.range.first - otherTextEditWithOffsets.range.first;
      if (res == 0) {
        return this.range.second - otherTextEditWithOffsets.range.second;
      }
      return res;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof TextEditWithOffsets otherEdit)) {
        return false;
      }
      return range.equals(otherEdit.range);
    }

    @Override
    public String toString() {
      return "range: " + range + ", newText: " + newText;
    }
  }

  @NotNull
  static public List<@NotNull TextEditWithOffsets> toTreeSetOfEditsWithOffsets(
      @NotNull ArrayList<@NotNull TextEdit> list,
      @NotNull Document document) {
    return list.stream().map(textEdit -> {
      var range = textEdit.getRange();
      return new TextEditWithOffsets(
          MiscUtil.positionToOffset(document, range.getStart()),
          MiscUtil.positionToOffset(document, range.getEnd()), textEdit.getNewText());
    }).toList();
  }

  static public @NotNull List<@NotNull TextEdit> toListOfTextEdits(
      @NotNull List<@NotNull TextEditWithOffsets> additionalEdits,
      @NotNull Document document) {
    return additionalEdits.stream().map(editWithOffsets -> new TextEdit(
        new Range(
            MiscUtil.offsetToPosition(document, editWithOffsets.range.first),
            MiscUtil.offsetToPosition(document, editWithOffsets.range.second)
        ),
        editWithOffsets.newText)).toList();
  }


  static public Pair<String, List<TextEditWithOffsets>> mergeTextEditsFromMainRangeToCaret(
      @NotNull List<@NotNull TextEditWithOffsets> diffRangesAsOffsetsList,
      int replaceElementStartOffset,
      int replaceElementEndOffset,
      @NotNull String originalText,
      int caretOffsetAfterInsert
  ) {
    var diffRangesAsOffsetsTreeSet = new TreeSet<>(diffRangesAsOffsetsList);
    var additionalEdits = new ArrayList<TextEditWithOffsets>();

    var textEditWithCaret = findEditWithCaret(diffRangesAsOffsetsTreeSet, caretOffsetAfterInsert);

    diffRangesAsOffsetsTreeSet.add(textEditWithCaret);
    final int selectedEditRangeStartOffset = textEditWithCaret.range.first;
    final int selectedEditRangeEndOffset = textEditWithCaret.range.second;

    final int collisionRangeStartOffset = Integer.min(selectedEditRangeStartOffset,
        replaceElementStartOffset);
    final int collisionRangeEndOffset = Integer.max(selectedEditRangeEndOffset,
        replaceElementEndOffset);

    var editsToMergeRangesAsOffsets = findIntersectedEdits(
        collisionRangeStartOffset,
        collisionRangeEndOffset,
        diffRangesAsOffsetsTreeSet,
        additionalEdits);

    return new Pair<>(
        mergeTextEditsToOneEditsString(
            editsToMergeRangesAsOffsets,
            replaceElementStartOffset,
            replaceElementEndOffset,
            additionalEdits,
            originalText),
        additionalEdits);
  }

  static private String mergeTextEditsToOneEditsString(TreeSet<TextEditWithOffsets> editsToMergeRangesAsOffsets,
                                                       int replaceElementStartOffset,
                                                       int replaceElementEndOffset,
                                                       ArrayList<TextEditWithOffsets> additionalEdits,
                                                       String originalText) {
    final var mergeRangeStartOffset = editsToMergeRangesAsOffsets.first().range.first;
    final var mergeRangeEndOffset = editsToMergeRangesAsOffsets.last().range.second;
    StringBuilder builder = new StringBuilder();
    if (mergeRangeStartOffset > replaceElementStartOffset) {
      builder.append(
          originalText,
          replaceElementStartOffset,
          mergeRangeStartOffset);
    } else if (mergeRangeStartOffset != replaceElementStartOffset) {
      additionalEdits.add(
          new TextEditWithOffsets(mergeRangeStartOffset, replaceElementStartOffset, ""));
    }
    var prevEndOffset = editsToMergeRangesAsOffsets.first().range.first;
    for (var editToMerge : editsToMergeRangesAsOffsets) {
      builder.append(
          originalText,
          prevEndOffset,
          editToMerge.range.first);

      prevEndOffset = editToMerge.range.second;

      builder.append(editToMerge.newText);
    }

    if (mergeRangeEndOffset < replaceElementEndOffset) {
      builder.append(originalText,
          mergeRangeEndOffset,
          replaceElementEndOffset);
    } else if (replaceElementEndOffset != mergeRangeEndOffset) {
      additionalEdits.add(
          new TextEditWithOffsets(replaceElementEndOffset, mergeRangeEndOffset, ""));
    }
    return builder.toString();
  }

  private static TreeSet<TextEditWithOffsets> findIntersectedEdits(
      int collisionRangeStartOffset,
      int collisionRangeEndOffset,
      TreeSet<TextEditWithOffsets> diffRangesAsOffsetsTreeSet,
      List<TextEditWithOffsets> uselessEdits) {

    var first = new TextEditWithOffsets(collisionRangeStartOffset,
        collisionRangeStartOffset, "");
    var last = new TextEditWithOffsets(collisionRangeEndOffset, collisionRangeEndOffset, "");
    var floor = diffRangesAsOffsetsTreeSet.floor(first);
    var ceil = diffRangesAsOffsetsTreeSet.ceiling(last);
    var editsToMergeRangesAsOffsets = new TreeSet<>(diffRangesAsOffsetsTreeSet.subSet(first, true, last, true));

    if (floor != null) {
      if (floor.range.second >= collisionRangeStartOffset) {
        editsToMergeRangesAsOffsets.add(floor);
        uselessEdits.addAll(diffRangesAsOffsetsTreeSet.headSet(floor, false));
      } else {
        uselessEdits.addAll(diffRangesAsOffsetsTreeSet.headSet(floor, true));
      }
    }

    if (ceil != null) {
      if (ceil.range.first <= collisionRangeEndOffset) {
        editsToMergeRangesAsOffsets.add(ceil);
        uselessEdits.addAll(diffRangesAsOffsetsTreeSet.tailSet(ceil, false));
      } else {
        uselessEdits.addAll(diffRangesAsOffsetsTreeSet.tailSet(ceil, true));
      }
    }
    return editsToMergeRangesAsOffsets;
  }

  static private TextEditWithOffsets findEditWithCaret(TreeSet<TextEditWithOffsets> diffRangesAsOffsetsTreeSet,
                                                       int caretOffsetAcc) {
    int sub;
    int prevEnd = 0;
    TextEditWithOffsets textEditWithCaret = null;
    for (TextEditWithOffsets editWithOffsets : diffRangesAsOffsetsTreeSet) {
      sub = (editWithOffsets.range.first - prevEnd);
      prevEnd = editWithOffsets.range.second;
      caretOffsetAcc -= sub;
      if (caretOffsetAcc < 0) {
        caretOffsetAcc += sub;
        textEditWithCaret = new TextEditWithOffsets(caretOffsetAcc, caretOffsetAcc, "$0");
        break;
      }
      sub = editWithOffsets.newText.length();
      caretOffsetAcc -= sub;
      if (caretOffsetAcc <= 0) {
        caretOffsetAcc += sub;
        editWithOffsets.newText = editWithOffsets.newText.substring(0, caretOffsetAcc) +
            "$0" + editWithOffsets.newText.substring(caretOffsetAcc);
        textEditWithCaret = editWithOffsets;
        break;
      }
    }
    if (textEditWithCaret == null) {
      var caretOffsetInOriginalDoc = prevEnd + caretOffsetAcc;
      textEditWithCaret =
          new TextEditWithOffsets(
              caretOffsetInOriginalDoc, caretOffsetInOriginalDoc, "$0");
    }
    return textEditWithCaret;
  }
}
