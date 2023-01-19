package org.rri.ideals.server.completions.util;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@RunWith(JUnit4.class)
public class TextEditRearrangerTest {
  public static Stream<Arguments> provideAllCasesForMerge() {
    return Stream.of(
        // |__| [!_!]
        Arguments.of(
            0, 1, // start, end
            new TextRange(2, 3), // caret
            List.of(new TextEditWithOffsets(2, 5, "___")), // textEdits
            "01___", // merged string
            Set.of(new TextEditWithOffsets(1, 5, "")) // expected additional edits
        ),
        // |__| [!_] [_!]
        Arguments.of(
            0, 1, // start, end
            new TextRange(2, 6), // caret
            List.of(
                new TextEditWithOffsets(2, 3, "_"),
                new TextEditWithOffsets(5, 6, "_")
            ), // textEdits
            "01_34_", // merged string
            Set.of(
                new TextEditWithOffsets(1, 6, "")) // expected additional edits
        ),
        // |__| [!_] [__] [_!]
        Arguments.of(
            0, 1, // start, end
            new TextRange(2, 7), // caret
            List.of(
                new TextEditWithOffsets(2, 3, "_"),
                new TextEditWithOffsets(4, 5, "_"),
                new TextEditWithOffsets(6, 7, "_")
            ), // textEdits
            "01_3_5_", // merged string
            Set.of(new TextEditWithOffsets(1, 7, "")) // expected additional edits
        ),
        // |!_| [_!]
        Arguments.of(
            0, 1, // start, end
            new TextRange(0, 4), // caret
            List.of(new TextEditWithOffsets(3, 4, "_")), // textEdits
            "012_", // merged string
            Set.of(
                new TextEditWithOffsets(1, 4, "")) // expected additional edits
        ),
        // [!_] |_!|
        Arguments.of(
            2, 3, // start, end
            new TextRange(0, 3), // caret
            List.of(new TextEditWithOffsets(0, 1, "_")), // textEdits
            "_12", // merged string
            Set.of(
                new TextEditWithOffsets(0, 2, "")) // expected additional edits
        ),
        // [|!_!|] [__]
        Arguments.of(
            0, 1, // start, end
            new TextRange(0, 1), // caret
            List.of(
                new TextEditWithOffsets(0, 1, "_"),
                new TextEditWithOffsets(3, 4, "_")
                ), // textEdits
            "_", // merged string
            Set.of(
                new TextEditWithOffsets(3, 4, "_")) // expected additional edits
        ),
        // [__] [!_!] |__|
        Arguments.of(
            3, 4, // start, end
            new TextRange(2, 3), // caret
            List.of(
                new TextEditWithOffsets(2, 3, "_"),
                new TextEditWithOffsets(0, 1, "_")), // textEdits
            "_3", // merged string
            Set.of(
                new TextEditWithOffsets(2, 3, ""),
                new TextEditWithOffsets(0, 1, "_")) // expected additional edits
        ),
        // [!_] [_!] |__|
        Arguments.of(
            5, 6, // start, end
            new TextRange(0, 2), // caret
            List.of(
                new TextEditWithOffsets(1, 2, "_"),
                new TextEditWithOffsets(2, 3, "_")
                ), // textEdits
            "__345", // merged string
            Set.of(
                new TextEditWithOffsets(1, 5, "")) // expected additional edits
        ),
        // [!_] [__] [_!] |__|
        Arguments.of(
            7, 8, // start, end
            new TextRange(0, 5), // caret
            List.of(
                new TextEditWithOffsets(1, 2, "_"),
                new TextEditWithOffsets(3, 4, "_"),
                new TextEditWithOffsets(5, 6, "_")
            ), // textEdits
            "_2_4_67", // merged string
            Set.of(
                new TextEditWithOffsets(1, 7, "")) // expected additional edits
        ),
        // [!_!] [__]  |__|
        Arguments.of(
            7, 8, // start, end
            new TextRange(0, 2), // caret
            List.of(
                new TextEditWithOffsets(0, 1, "__"),
                new TextEditWithOffsets(4, 5, "")
            ), // textEdits
            "__123567", // merged string
            Set.of(
                new TextEditWithOffsets(0, 7, "")) // expected additional edits
        ),
        // [__|]!_![|__]
        Arguments.of(
            3, 5, // start, end
            new TextRange(3, 5), // caret
            List.of(
                new TextEditWithOffsets(2, 4, "__"),
                new TextEditWithOffsets(4, 6, "__")
            ), // textEdits
            "____", // merged string
            Set.of(
                new TextEditWithOffsets(2, 3, ""),
                new TextEditWithOffsets(5, 6, "")) // expected additional edits
        )
    );
  }

  @NotNull
  final String simpleSampleText = "0123456789";

  @Test
  public void voidTest() { // gradle fails to execute this test if there is no methods with @Test annotation
  }

  // |__| -- main replace range
  // [__] -- TextEdit from diff
  //  !   -- caret
  @ParameterizedTest
  @MethodSource("provideAllCasesForMerge")
  public void testMergeTextEditsFromMainRangeToCaret(int replaceElementStartOffset,
                                                     int replaceElementEndOffset,
                                                     @NotNull TextRange snippetBounds,
                                                     @NotNull List<TextEditWithOffsets> diffRangesAsOffsetsList,
                                                     @NotNull String expectedString,
                                                     @NotNull Set<TextEditWithOffsets> expectedAdditionalEdits) {

    var res = TextEditRearranger.findOverlappingTextEditsInRangeFromMainTextEditToSnippetsAndMergeThem(
        diffRangesAsOffsetsList,
        replaceElementStartOffset,
        replaceElementEndOffset,
        simpleSampleText,
        snippetBounds
        );
    Assertions.assertEquals(expectedString, res.mainEdit().getNewText());
    Assertions.assertEquals(expectedAdditionalEdits, new HashSet<>(res.additionalEdits()));
  }
}
