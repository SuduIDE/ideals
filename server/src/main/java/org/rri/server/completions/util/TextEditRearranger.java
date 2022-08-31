package org.rri.server.completions.util;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
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

    var textEditWithCaret = findAndTransformEditWithCaret(diffRangesAsOffsetsTreeSet, caretOffsetAfterInsert);

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

  /**
   * Finds (or creates and adds into the given sorted set) an edit, inside which the caret is positioned,
   * and places caret marker inside its text into the proper position.
   * @param sortedDiffRanges mutable set with text edits sorted by position
   * @param caretOffset absolute caret offset
   * @return the found or created text edit with marked text
   */
  @NotNull
  static private TextEditWithOffsets findAndTransformEditWithCaret(
      @NotNull SortedSet<TextEditWithOffsets> sortedDiffRanges,
      int caretOffset) {
    int sub;
    int prevEnd = 0;
    int currentRelativeCaretOffset = caretOffset;

    TextEditWithOffsets textEditWithCaret = null;
    for (TextEditWithOffsets editWithOffsets : sortedDiffRanges) {
      sub = (editWithOffsets.getRange().getStartOffset() - prevEnd);
      prevEnd = editWithOffsets.getRange().getEndOffset();

      if (currentRelativeCaretOffset < sub) { // not found
        textEditWithCaret = new TextEditWithOffsets(currentRelativeCaretOffset, currentRelativeCaretOffset, "$0");
        break;
      }

      currentRelativeCaretOffset -= sub;

      sub = editWithOffsets.getNewText().length();
      if (currentRelativeCaretOffset <= sub) {
        final var textWithCaret = editWithOffsets.getNewText().substring(0, currentRelativeCaretOffset) +
            "$0" + editWithOffsets.getNewText().substring(currentRelativeCaretOffset);
        sortedDiffRanges.remove(editWithOffsets);

        editWithOffsets = new TextEditWithOffsets(editWithOffsets.getRange(), textWithCaret);
        textEditWithCaret = editWithOffsets;
        break;
      }

      currentRelativeCaretOffset -= sub;
    }

    if (textEditWithCaret == null) {  // still not found
      var caretOffsetInOriginalDoc = prevEnd + currentRelativeCaretOffset;
      textEditWithCaret = new TextEditWithOffsets(caretOffsetInOriginalDoc, caretOffsetInOriginalDoc, "$0");
    }

    sortedDiffRanges.add(textEditWithCaret);

    return textEditWithCaret;
  }
}
