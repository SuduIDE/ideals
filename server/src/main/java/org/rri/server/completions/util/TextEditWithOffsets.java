package org.rri.server.completions.util;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.jetbrains.annotations.NotNull;
import org.rri.server.util.MiscUtil;

public class TextEditWithOffsets implements Comparable<TextEditWithOffsets> {
  @NotNull
  private final TextRange range;
  @NotNull
  private String newText;

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

  // TODO make it immutable
  void setNewText(@NotNull String newText) {
    this.newText = newText;
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
