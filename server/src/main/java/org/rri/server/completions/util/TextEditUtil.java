package org.rri.server.completions.util;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public class TextEditUtil {
  static public class TextEditWithOffsets implements Comparable<TextEditWithOffsets> {
    @NotNull
    private final Pair<Integer, Integer> range;
    @NotNull
    private String newText;

    @NotNull
    public String getNewText() {
      return newText;
    }

    public @NotNull Pair<Integer, Integer> getRange() {
      return range;
    }

    public TextEditWithOffsets(@NotNull Integer start, @NotNull Integer end, @NotNull String newText) {
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
    @NotNull
    public String toString() {
      return "range: " + range + ", newText: " + newText;
    }
  }



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
      boolean isLowerBoundInclusive = floor.range.second >= collisionRangeStartOffset;
      if (isLowerBoundInclusive) {
        editsToMergeRangesAsOffsets.add(floor);
      }
      uselessEdits.addAll(diffRangesAsOffsetsTreeSet.headSet(floor, !isLowerBoundInclusive));
    }

    if (ceil != null) {
      boolean isUpperBoundInclusive = ceil.range.first <= collisionRangeEndOffset;
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

  public record MergeEditsResult(@NotNull TextEditWithOffsets mainEdit,
                          @NotNull List<TextEditWithOffsets> additionalEdits) {
  }
}
