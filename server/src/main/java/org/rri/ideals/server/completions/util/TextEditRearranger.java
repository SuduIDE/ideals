package org.rri.ideals.server.completions.util;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public class TextEditRearranger {
  /**
   * VScode doesn't allow to change main TextEdit's range during resolve, but allows to change
   * its text and additional edits. Also, snippets are allowed only in main TextEdit. So solution
   * is to find text edits, that have intersecting ranges with range from text edit to snippets, and
   * merge them, as if they were a single text edit with range from main text edit. It is more
   * understandable in example:<p>
   * Text = 1234567. You have the main TextEdit with range [2, 3]: 1|23|4567. Diff is equal to list
   * of TextEdits = [[1] -> "a", [3, 4] -> "_"]. And you have a caret ! after insert placed at 5.
   * Original text with marked ranges is: [1]|2[3|4]5!67. Text that we want to see after insert =
   * a2_5!67. We want to place text "...5!" into main TextEdit and also main range is intersecting
   * with TextEdit from diff.
   * Solution is to paste into main TextEdit's text "2_5$0" ($0 - is interpreted by lsp as a
   * caret) and delete diff's TextEdit. If we leave it as it is at this stage, we will get:
   * 12_5!4567. As you see, we need to delete text, that was in previous TextEdit from diff, and
   * text, that was between TextEdits and caret. We add this new delete TextEdit to additional
   * TextEdits: [4, 5] -> "". Also we need to add not intersected TextEdit to additional
   * TextEdits. Result text after this operation = a2_5!67, additional text edits = [[4, 5] ->
   * "", [1, 1] -> "a"]]
   * @param diffRangesAsOffsetsList a diff's TextEdits
   * @param replaceElementStartOffset the main TextEdit's range start
   * @param replaceElementEndOffset the main TextEdit's range end
   * @param originalText document's text *before* insert
   * @param snippetBounds snippetBound position *after* insert
   * @return Additional TextEdits and new main TextEdit
   */
  @NotNull
  static public MergeEditsResult findOverlappingTextEditsInRangeFromMainTextEditToSnippetsAndMergeThem(
      @NotNull List<@NotNull TextEditWithOffsets> diffRangesAsOffsetsList,
      int replaceElementStartOffset,
      int replaceElementEndOffset,
      @NotNull String originalText,
      @NotNull TextRange snippetBounds
  ) {
    var diffRangesAsOffsetsTreeSet = new TreeSet<>(diffRangesAsOffsetsList);
    var additionalEdits = new ArrayList<TextEditWithOffsets>();

    final int firstTextEditWithSnippetStartOffset =
        findRangeOfTextEditWithSnippetBound(diffRangesAsOffsetsTreeSet,
        snippetBounds.getStartOffset()).getStartOffset();
    final int lastTextEditWithSnippetEndOffset =
        findRangeOfTextEditWithSnippetBound(diffRangesAsOffsetsTreeSet,
            snippetBounds.getEndOffset()).getEndOffset();

    final int collisionRangeStartOffset = Integer.min(firstTextEditWithSnippetStartOffset,
        replaceElementStartOffset);
    final int collisionRangeEndOffset = Integer.max(lastTextEditWithSnippetEndOffset,
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

  /**
   * Here we are merging intersected edits in range from main TextEdit to snippetBound
   * @param editsToMergeRangesAsOffsets intersected edits
   * @param replaceElementStartOffset main TextEdit's range start
   * @param replaceElementEndOffset main TextEdit's range end
   * @param additionalEdits additional edits list that will achieve new delete TextEdits
   * @param originalText text *before* insert
   * @return Text edit, that has a range = [replaceElementStartOffset, replaceElementEndOffset]
   * and a text, that contains all text that edits will insert as they were a single text edit.
   */
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

  /**
   * Find intersected TextEdits with range [collisionRangeStartOffset, collisionRangeEndOffset]
   * @param collisionRangeStartOffset min(main TextEdit's start, snippetBound)
   * @param collisionRangeEndOffset max(main TextEdit's end, snippetBound)
   * @param diffRangesAsOffsetsTreeSet sorted diff TextEdits
   * @param uselessEdits aka additional TextEdits, that will achieve diff's TextEdits, that are
   *                     not intersecting with collision range
   * @return intersected TextEdits
   */
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
   * Finds range of edit, inside which the snippet bound is positioned.
   * @param sortedDiffRanges mutable set with text edits sorted by position
   * @param snippetBound absolute snippetBound offset
   * @return the found or created text edit with marked text
   */
  @NotNull
  static private TextRange findRangeOfTextEditWithSnippetBound(
      @NotNull SortedSet<TextEditWithOffsets> sortedDiffRanges,
      int snippetBound) {
    int sub;
    int prevEnd = 0;
    int currentRelativeBoundOffset = snippetBound;

    TextRange rangeWithSnippetBound = null;
    for (TextEditWithOffsets editWithOffsets : sortedDiffRanges) {
      sub = (editWithOffsets.getRange().getStartOffset() - prevEnd);
      if (currentRelativeBoundOffset < sub) { // not found
        var boundOffsetInOriginalDoc = prevEnd + currentRelativeBoundOffset;
        rangeWithSnippetBound = new TextRange(
            boundOffsetInOriginalDoc, boundOffsetInOriginalDoc);
        break;
      }

      currentRelativeBoundOffset -= sub;

      sub = editWithOffsets.getNewText().length();
      if (currentRelativeBoundOffset <= sub) {
        rangeWithSnippetBound = editWithOffsets.getRange();
        break;
      }

      currentRelativeBoundOffset -= sub;
      prevEnd = editWithOffsets.getRange().getEndOffset();
    }

    if (rangeWithSnippetBound == null) {  // still not found
      var boundOffsetInOriginalDoc = prevEnd + currentRelativeBoundOffset;
      rangeWithSnippetBound = new TextRange(boundOffsetInOriginalDoc, boundOffsetInOriginalDoc);
    }

    return rangeWithSnippetBound;
  }
}
