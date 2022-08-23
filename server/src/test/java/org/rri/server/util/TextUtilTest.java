package org.rri.server.util;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.server.util.TextUtil.TextEditWithOffsets;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@RunWith(JUnit4.class)
public class TextUtilTest {
  @Test
  public void voidTest() { // todo why without this tests are failed
  }

  @NotNull
  final String simpleSampleText = "0123456789";

  // |__| -- main replace range
  // [__] -- TextEdit from diff
  //  !   -- caret
  @ParameterizedTest
  @MethodSource("provideAllCasesForMerge")
  public void testMergeTextEditsFromMainRangeToCaret(int replaceElementStartOffset,
                                                     int replaceElementEndOffset,
                                                     int caretOffsetAfterInsert,
                                                     List<TextEditWithOffsets> diffRangesAsOffsetsList,
                                                     String expectedString,
                                                     Set<TextEditWithOffsets> expectedAdditionalEdits) {

    var res = TextUtil.mergeTextEditsFromMainRangeToCaret(
        diffRangesAsOffsetsList,
        replaceElementStartOffset,
        replaceElementEndOffset,
        simpleSampleText,
        caretOffsetAfterInsert);
    Assertions.assertEquals(expectedString, res.first);
    Assertions.assertEquals(expectedAdditionalEdits, new HashSet<>(res.second));
  }


  public static Stream<Arguments> provideAllCasesForMerge() {
    return Stream.of(
        // |__| [__] !
        Arguments.of(
            0, 1, // start, end
            4, // caret
            List.of(new TextEditWithOffsets(2, 3, "_")), // textEdits
            "01_3$0", // merged string
            Set.of(new TextEditWithOffsets(1, 4, "")) // expected additional edits
        ),
        // |__| [_!]
        Arguments.of(
            0, 1, // start, end
            3, // caret
            List.of(new TextEditWithOffsets(2, 4, "___")), // textEdits
            "01_$0__", // merged string
            Set.of(new TextEditWithOffsets(1, 4, "")) // expected additional edits
        ),
        // |__| ! [__]
        Arguments.of(
            0, 1, // start, end
            2, // caret
            List.of(new TextEditWithOffsets(3, 4, "_")), // textEdits
            "01$0", // merged string
            Set.of(
                new TextEditWithOffsets(1, 2, ""),
                new TextEditWithOffsets(3, 4, "_")) // expected additional edits
        ),
        // |__| ! [__]
        Arguments.of(
            0, 1, // start, end
            2, // caret
            List.of(new TextEditWithOffsets(3, 4, "_")), // textEdits
            "01$0", // merged string
            Set.of(
                new TextEditWithOffsets(1, 2, ""),
                new TextEditWithOffsets(3, 4, "_")) // expected additional edits
        ),
        // [__] |__| !
        Arguments.of(
            2, 3, // start, end
            4, // caret
            List.of(new TextEditWithOffsets(0, 1, "_")), // textEdits
            "23$0", // merged string
            Set.of(
                new TextEditWithOffsets(0, 1, "_"),
                new TextEditWithOffsets(3, 4, "")) // expected additional edits
        ),
        // [__] ! |__|
        Arguments.of(
            3, 4, // start, end
            4, // caret
            List.of(new TextEditWithOffsets(0, 1, "___")), // textEdits
            "$023", // merged string
            Set.of(
                new TextEditWithOffsets(0, 1, "___"),
                new TextEditWithOffsets(2, 3, "")) // expected additional edits
        ),
        // ! [__] |__|
        Arguments.of(
            3, 4, // start, end
            0, // caret
            List.of(new TextEditWithOffsets(1, 2, "")), // textEdits
            "$0023", // merged string
            Set.of(
                new TextEditWithOffsets(0, 3, "")) // expected additional edits
        ),
        // [!_] |__|
        Arguments.of(
            3, 4, // start, end
            2, // caret
            List.of(new TextEditWithOffsets(1, 2, "___")), // textEdits
            "_$0__23", // merged string
            Set.of(
                new TextEditWithOffsets(1, 3, "")) // expected additional edits
        ),
        // [__|]__[|__] !
        Arguments.of(
            3, 5, // start, end
            7, // caret
            List.of(
                new TextEditWithOffsets(2, 4, "___"),
                new TextEditWithOffsets(4, 6, "")
            ), // textEdits
            "___67$0", // merged string
            Set.of(
                new TextEditWithOffsets(2, 3, ""),
                new TextEditWithOffsets(5, 8, "")) // expected additional edits
        ),
        // ! [__|]__[|__]
        Arguments.of(
            3, 5, // start, end
            0, // caret
            List.of(
                new TextEditWithOffsets(2, 4, ""),
                new TextEditWithOffsets(4, 6, "___")
            ), // textEdits
            "$001___", // merged string
            Set.of(
                new TextEditWithOffsets(0, 3, ""),
                new TextEditWithOffsets(5, 6, "")) // expected additional edits
        )
    );
  }
}
