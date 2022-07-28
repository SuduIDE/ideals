package org.rri.server.formatting;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.python.PythonFileType;
import org.eclipse.lsp4j.TextEdit;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.server.TestUtil;
import org.rri.server.commands.ExecutorContext;
import org.rri.server.util.MiscUtil;

import java.util.ArrayList;
import java.util.List;

@RunWith(JUnit4.class)
public class FormattingCommandTest extends BasePlatformTestCase {

  @Test
  public void testFormatting() {
    List<List<TextEdit>> expectedTestResults = new ArrayList<>();
    List<List<TextEdit>> actualTestResults = new ArrayList<>();

    actualTestResults.add(getEditsByText("", "", PythonFileType.INSTANCE));
    expectedTestResults.add(List.of());

    actualTestResults.add(getEditsByText("x = 10", "x = 10", PythonFileType.INSTANCE));
    expectedTestResults.add(List.of());

    actualTestResults.add(getEditsByText("def foo(x): x = 1", "def foo(x): x = 1", PythonFileType.INSTANCE));
    expectedTestResults.add(List.of());

    actualTestResults.add(getEditsByText("\n     x=10", "x = 10", PythonFileType.INSTANCE));
    expectedTestResults.add(List.of(
        TestUtil.createTextEdit(0, 0, 1, 5, ""),
        TestUtil.createTextEdit(1, 6, 1, 6, " "),
        TestUtil.createTextEdit(1, 7, 1, 7, " ")));

    actualTestResults.add(getEditsByText(
        "def  foo  (x)   : x = 1\nx = 1", "def foo(x): x = 1\n\n\nx = 1", PythonFileType.INSTANCE));
    expectedTestResults.add(List.of(
        TestUtil.createTextEdit(0, 4, 0, 5, ""),
        TestUtil.createTextEdit(0, 8, 0, 10, ""),
        TestUtil.createTextEdit(0, 13, 0, 16, ""),
        TestUtil.createTextEdit(1, 0, 1, 0, "\n\n")
    ));

    actualTestResults.add(getEditsByText(
        "for i in range(10):\ni += 1", "for i in range(10):\n    i += 1", PythonFileType.INSTANCE));
    expectedTestResults.add(List.of(
        TestUtil.createTextEdit(1, 0, 1, 0, "    ")
    ));

    actualTestResults.add(getEditsByText(
        "import sys x = 1", "import sys\n\nx = 1", PythonFileType.INSTANCE));
    expectedTestResults.add(List.of(
        TestUtil.createTextEdit(0, 10, 0, 11, "\n\n")
    ));

    Assertions.assertEquals(expectedTestResults, actualTestResults);
  }

  @NotNull
  public List<@NotNull TextEdit> getEditsByText(@NotNull String actualText,
                                                @NotNull String expectedText,
                                                @NotNull FileType fileType) {
    final var actualPsiFile = myFixture.configureByText(fileType, actualText);

    var context = new ExecutorContext(actualPsiFile, getProject(), new TestUtil.DumbCancelChecker());
    var command = new FormattingCommand(null, FormattingCommandTests.defaultOptions());

    return MiscUtil.differenceAfterAction(actualPsiFile, (copy) -> {
      command.reformatPsiFile(context, copy);
      Assertions.assertNotEquals(actualPsiFile, copy);
      Assertions.assertEquals(expectedText, copy.getText());
    });
  }


  @Override
  protected String getTestDataPath() {
    return "test-data/formatting/formatting-project";
  }
}
