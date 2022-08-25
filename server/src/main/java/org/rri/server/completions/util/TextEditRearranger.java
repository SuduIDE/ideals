package org.rri.server.completions.util;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public class TextEditRearranger {

  static public MergeEditsResult findOverlappingTextEditsInRangeFromMainTextEditToCaretAndMergeThem(
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
    final int selectedEditRangeStartOffset = textEditWithCaret.getRange().getStartOffset();
    final int selectedEditRangeEndOffset = textEditWithCaret.getRange().getEndOffset();

    final int collisionRangeStartOffset = Integer.min(selectedEditRangeStartOffset,
        replaceElementStartOffset);
    final int collisionRangeEndOffset = Integer.max(selectedEditRangeEndOffset,
        replaceElementEndOffset);

    var editsToMergeRangesAsOffsets = findIntersectedEdits(
        collisionRangeStartOffset,
        collisionRangeEndOffset,
        diffRangesAsOffsetsTreeSet,
        additionalEdits);

    return new MergeEditsResult(
        mergeTextEditsToOne(
            editsToMergeRangesAsOffsets,
            replaceElementStartOffset,
            replaceElementEndOffset,
            additionalEdits,
            originalText),
        additionalEdits);
  }

  @NotNull
  static private TextEditWithOffsets mergeTextEditsToOne(
      @NotNull TreeSet<TextEditWithOffsets> editsToMergeRangesAsOffsets,
      int replaceElementStartOffset,
      int replaceElementEndOffset,
      @NotNull ArrayList<TextEditWithOffsets> additionalEdits,
      @NotNull String originalText) {
    final var mergeRangeStartOffset = editsToMergeRangesAsOffsets.first().getRange().getStartOffset();
    final var mergeRangeEndOffset = editsToMergeRangesAsOffsets.last().getRange().getEndOffset();
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
    var prevEndOffset = editsToMergeRangesAsOffsets.first().getRange().getStartOffset();
    for (var editToMerge : editsToMergeRangesAsOffsets) {
      builder.append(
          originalText,
          prevEndOffset,
          editToMerge.getRange().getStartOffset());

      prevEndOffset = editToMerge.getRange().getEndOffset();

      builder.append(editToMerge.getNewText());
    }

    if (mergeRangeEndOffset < replaceElementEndOffset) {
      builder.append(originalText,
          mergeRangeEndOffset,
          replaceElementEndOffset);
    } else if (replaceElementEndOffset != mergeRangeEndOffset) {
      additionalEdits.add(
          new TextEditWithOffsets(replaceElementEndOffset, mergeRangeEndOffset, ""));
    }
    return new TextEditWithOffsets(replaceElementStartOffset, replaceElementEndOffset, builder.toString());
  }

  @NotNull
  private static TreeSet<TextEditWithOffsets> findIntersectedEdits(
      int collisionRangeStartOffset,
      int collisionRangeEndOffset,
      @NotNull TreeSet<TextEditWithOffsets> diffRangesAsOffsetsTreeSet,
      @NotNull List<TextEditWithOffsets> uselessEdits) {

    var first = new TextEditWithOffsets(collisionRangeStartOffset,
        collisionRangeStartOffset, "");
    var last = new TextEditWithOffsets(collisionRangeEndOffset, collisionRangeEndOffset, "");
    var floor = diffRangesAsOffsetsTreeSet.floor(first);
    var ceil = diffRangesAsOffsetsTreeSet.ceiling(last);
    var editsToMergeRangesAsOffsets = new TreeSet<>(diffRangesAsOffsetsTreeSet.subSet(first, true, last, true));

    if (floor != null) {
      boolean isLowerBoundInclusive = floor.getRange().getEndOffset() >= collisionRangeStartOffset;
      if (isLowerBoundInclusive) {
        editsToMergeRangesAsOffsets.add(floor);
      }
      uselessEdits.addAll(diffRangesAsOffsetsTreeSet.headSet(floor, !isLowerBoundInclusive));
    }

    if (ceil != null) {
      boolean isUpperBoundInclusive = ceil.getRange().getStartOffset() <= collisionRangeEndOffset;
      if (isUpperBoundInclusive) {
        editsToMergeRangesAsOffsets.add(ceil);
      }
      uselessEdits.addAll(diffRangesAsOffsetsTreeSet.tailSet(ceil, !isUpperBoundInclusive));
    }
    return editsToMergeRangesAsOffsets;
  }

  @NotNull
  static private TextEditWithOffsets findEditWithCaret(
      @NotNull TreeSet<TextEditWithOffsets> diffRangesAsOffsetsTreeSet,
      int caretOffsetAcc) {
    int sub;
    int prevEnd = 0;
    TextEditWithOffsets textEditWithCaret = null;
    for (TextEditWithOffsets editWithOffsets : diffRangesAsOffsetsTreeSet) {
      sub = (editWithOffsets.getRange().getStartOffset() - prevEnd);
      prevEnd = editWithOffsets.getRange().getEndOffset();
      caretOffsetAcc -= sub;
      if (caretOffsetAcc < 0) {
        caretOffsetAcc += sub;
        textEditWithCaret = new TextEditWithOffsets(caretOffsetAcc, caretOffsetAcc, "$0");
        break;
      }
      sub = editWithOffsets.getNewText().length();
      caretOffsetAcc -= sub;
      if (caretOffsetAcc <= 0) {
        caretOffsetAcc += sub;
        final var textWithCaret = editWithOffsets.getNewText().substring(0, caretOffsetAcc) +
            "$0" + editWithOffsets.getNewText().substring(caretOffsetAcc);
        editWithOffsets.setNewText(textWithCaret);
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
