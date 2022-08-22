package org.rri.server.textUtil;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.server.util.TextUtil;

import java.util.List;

@RunWith(JUnit4.class)
public class TextUtilTest extends BasePlatformTestCase {
  // |__| -- main replace range
  // [__] -- TextEdit from diff
  //  !   -- caret

  @Test
  public void testCase_MainDiffCaret() {
    // |__| [__] !
    int replaceElementStartOffset = 0;
    int replaceElementEndOffset = 1;
    @NotNull String originalText = "1234";
    int caretOffsetAfterInsert = 4;
    List<TextUtil.TextEditWithOffsets> diffRangesAsOffsetsList = List.of(
        new TextUtil.TextEditWithOffsets(2, 3, "2")
    );


  }

  @Test
  public void testCase_MainDiffWithCaret() {
    // |__| [_!]
    int replaceElementStartOffset = 0;
    int replaceElementEndOffset = 1;
    @NotNull String originalText = "1234";
    int caretOffsetAfterInsert = 4;
    List<TextUtil.TextEditWithOffsets> diffRangesAsOffsetsList = List.of(
        new TextUtil.TextEditWithOffsets(2, 3, "2")
    );


  }
  @Test
  public void testCase_MainCaretDiff() {
    // |__| ! [__]
    int replaceElementStartOffset = 0;
    int replaceElementEndOffset = 1;
    @NotNull String originalText = "1234";
    int caretOffsetAfterInsert = 4;
    List<TextUtil.TextEditWithOffsets> diffRangesAsOffsetsList = List.of(
        new TextUtil.TextEditWithOffsets(2, 3, "2")
    );


  }
  @Test
  public void testCase_CaretDiffMain() {
    // ! [__] |__|
    int replaceElementStartOffset = 0;
    int replaceElementEndOffset = 1;
    @NotNull String originalText = "1234";
    int caretOffsetAfterInsert = 4;
    List<TextUtil.TextEditWithOffsets> diffRangesAsOffsetsList = List.of(
        new TextUtil.TextEditWithOffsets(2, 3, "2")
    );


  }

  @Test
  public void testCase_DiffWithCaretMain() {
    // [!_] |__|
    int replaceElementStartOffset = 0;
    int replaceElementEndOffset = 1;
    @NotNull String originalText = "1234";
    int caretOffsetAfterInsert = 4;
    List<TextUtil.TextEditWithOffsets> diffRangesAsOffsetsList = List.of(
        new TextUtil.TextEditWithOffsets(2, 3, "2")
    );
  }

  @Test
  public void testCase_DiffCaretMain() {
    // [__] ! |__|
    int replaceElementStartOffset = 0;
    int replaceElementEndOffset = 1;
    @NotNull String originalText = "1234";
    int caretOffsetAfterInsert = 4;
    List<TextUtil.TextEditWithOffsets> diffRangesAsOffsetsList = List.of(
        new TextUtil.TextEditWithOffsets(2, 3, "2")
    );
  }
}
