package org.rri.ideals.server.completions.util;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.util.MiscUtil;

import java.util.Objects;

public class TextEditWithOffsets implements Comparable<TextEditWithOffsets> {
  @NotNull
  private final TextRange range;
  @NotNull
  private final String newText;

  public TextEditWithOffsets(@NotNull TextRange range, @NotNull String newText) {
    this.range = range;
    this.newText = newText;
  }

  public TextEditWithOffsets(int start, int end, @NotNull String newText) {
    this(new TextRange(start, end), newText);
  }

  public TextEditWithOffsets(@NotNull TextEdit textEdit, @NotNull Document document) {
    this(MiscUtil.positionToOffset(document, textEdit.getRange().getStart()),
        MiscUtil.positionToOffset(document, textEdit.getRange().getEnd()), textEdit.getNewText());
  }

  @NotNull
  public String getNewText() {
    return newText;
  }

  public @NotNull TextRange getRange() {
    return range;
  }

  @NotNull
  public TextEdit toTextEdit(@NotNull Document document) {
    return new TextEdit(
        new Range(
            MiscUtil.offsetToPosition(document, range.getStartOffset()),
            MiscUtil.offsetToPosition(document, range.getEndOffset())
        ),
        newText);
  }

  @Override
  public int compareTo(@NotNull TextEditWithOffsets otherTextEditWithOffsets) {
    int res = this.range.getStartOffset() - otherTextEditWithOffsets.range.getStartOffset();
    if (res == 0) {
      return this.range.getEndOffset() - otherTextEditWithOffsets.range.getEndOffset();
    }
    return res;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TextEditWithOffsets that = (TextEditWithOffsets) o;
    return range.equals(that.range) && newText.equals(that.newText);
  }

  @Override
  public int hashCode() {
    return Objects.hash(range, newText);
  }

  @Override
  @NotNull
  public String toString() {
    return "range: " + range + ", newText: " + newText;
  }
}
