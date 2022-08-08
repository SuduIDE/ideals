package org.rri.server.formatting;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.python.PythonFileType;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextEdit;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.server.TestUtil;
import org.rri.server.util.MiscUtil;
import org.rri.server.util.TextUtil;

import java.util.List;
import java.util.Objects;

@RunWith(JUnit4.class)
public class OnTypeFormattingCommandTest extends BasePlatformTestCase {
  @Test
  public void testFormatAfterClosedBlockJava() {
    Assertions.assertEquals(List.of(
            TestUtil.newTextEdit(1, 0, 1, 0, "    "),
            TestUtil.newTextEdit(1, 5, 1, 5, " "),
            TestUtil.newTextEdit(1, 7, 1, 9, ""),
            TestUtil.newTextEdit(1, 11, 1, 22, "")
        ),
        getEditsByTextAndInsertedCharAtPosition(
            """
                class Main {
                int x=   10           ;
                }
                """,
            """
                class Main {
                    int x = 10;
                }
                """,
            JavaFileType.INSTANCE, new Position(2, 1)
        ));
  }

  @Test
  public void testWrapBinaryExprJava() {
    Assertions.assertEquals(List.of(
            TestUtil.newTextEdit(1, 12, 1, 12, "("),
            TestUtil.newTextEdit(1, 18, 1, 18, ")")
        ),
        getEditsByTextAndInsertedCharAtPosition(
            """
                class Main {
                    int x = 10 & 7 ==;
                }
                """,
            """
                class Main {
                    int x = (10 & 7) ==;
                }
                """,
            JavaFileType.INSTANCE, new Position(1, 21)
        ));
  }

  @Test
  public void testSemiColonInsideFuncJava() {
    Assertions.assertEquals(List.of(
            TestUtil.newTextEdit(2, 12, 2, 13, ""),
            TestUtil.newTextEdit(2, 14, 2, 14, ";")
        ),
        getEditsByTextAndInsertedCharAtPosition(
            """
                class Main {
                    void foo() {
                        foo(;)
                    }
                }
                """,
            """
                class Main {
                    void foo() {
                        foo();
                    }
                }
                """,
            JavaFileType.INSTANCE, new Position(2, 13)
        ));
  }

  @Test
  public void testWrapBooleanExprInsideOtherJava() {
    Assertions.assertEquals(List.of(
            TestUtil.newTextEdit(1, 16, 1, 16, "("),
            TestUtil.newTextEdit(1, 22, 1, 22, ")")
        ),
        getEditsByTextAndInsertedCharAtPosition(
            """
                class Main {
                    int x = 1 + true ?
                }
                """,
            """
                class Main {
                    int x = 1 + (true ?)
                }
                """,
            JavaFileType.INSTANCE, new Position(1, 22)
        ));
  }

  @Test
  public void testSwitchIndentFormatAfterColonJava() {
    Assertions.assertEquals(List.of(
            TestUtil.newTextEdit(4, 12, 4, 16, "")
        ),
        getEditsByTextAndInsertedCharAtPosition(
            """
                class Main {
                    void foo(boolean x) {
                        switch (x) {
                            case true:
                                case false:
                        }
                    }
                }
                """,
            """
                class Main {
                    void foo(boolean x) {
                        switch (x) {
                            case true:
                            case false:
                        }
                    }
                }
                """,
            JavaFileType.INSTANCE, new Position(4, 27)
        ));
  }

  @Test
  public void testNotInsertSecondColon() {
    Assertions.assertEquals(List.of(
            TestUtil.newTextEdit(0, 3, 0, 4, "")
        ),
        getEditsByTextAndInsertedCharAtPosition(
            """
                if::
                """,
            """
                if:
                """,
            PythonFileType.INSTANCE, new Position(0, 3)
        ));
  }

  @Test
  public void testInsertDocstringOnSpace() {
    Assertions.assertEquals(List.of(
            TestUtil.newTextEdit(2, 0, 2, 0,
                """
                        :param first:\s
                        :return:\s
                        '''
                    """)
        ),
        getEditsByTextAndInsertedCharAtPosition(
            """
                def foo(first):
                    '''\s
                """,
            """
                def foo(first):
                    '''\s
                    :param first:\s
                    :return:\s
                    '''
                """,
            PythonFileType.INSTANCE, new Position(1, 8)
        ));
  }


  @NotNull
  private List<@NotNull TextEdit> getEditsByTextAndInsertedCharAtPosition(@NotNull String actualText,
                                                                          @NotNull String expectedText,
                                                                          @NotNull FileType fileType,
                                                                          @NotNull Position caretPosition) {
    final var actualPsiFile = myFixture.configureByText(fileType, actualText);
    var triggerCh = getInsertedChar(actualPsiFile, caretPosition);
    var command = new OnTypeFormattingCommand(
        caretPosition, FormattingTestUtil.defaultOptions(), triggerCh);

    return TextUtil.differenceAfterActionOnCopy(actualPsiFile, (copy) -> {
      command.typeAndReformatIfNeededInFile(copy);

      // some insert calls are not committing file
      PsiDocumentManager.getInstance(copy.getProject()).commitDocument(
          Objects.requireNonNull(MiscUtil.getDocument(copy)));

      Assertions.assertNotEquals(actualPsiFile, copy);
      Assertions.assertEquals(expectedText, copy.getText());
    });
  }

  private char getInsertedChar(@NotNull PsiFile psiFile, @NotNull Position caretPosition) {
    var doc = MiscUtil.getDocument(psiFile);
    assert doc != null;
    var caretOffset = MiscUtil.positionToOffset(doc, caretPosition);
    return doc.getText().charAt(caretOffset - 1);
  }

}
