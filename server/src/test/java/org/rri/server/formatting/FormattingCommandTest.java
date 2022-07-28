package org.rri.server.formatting;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.python.PythonFileType;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.FormattingOptions;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.server.LspContext;
import org.rri.server.TestUtil;
import org.rri.server.commands.ExecutorContext;
import org.rri.server.mocks.MockLanguageClient;
import org.rri.server.util.EditorUtil;
import org.rri.server.util.MiscUtil;

@RunWith(JUnit4.class)
public class FormattingCommandTest extends BasePlatformTestCase {
  @Before
  public void setupContext() {
    LspContext.createContext(getProject(),
        new MockLanguageClient(),
        new ClientCapabilities()
    );
  }

  @NotNull
  private MockLanguageClient getClient() {
    return (MockLanguageClient) LspContext.getContext(getProject()).getClient();
  }

  @Test
  public void testCompletionForKeywordsThatContainsLetterD() {
    final var actual = myFixture.configureByText(PythonFileType.INSTANCE, "    x = 10");
    final var expected = myFixture.configureByText(PythonFileType.INSTANCE, "x = 10");

    var context = new ExecutorContext(actual, getProject(), new TestUtil.DumbCancelChecker());
    var command = new FormattingCommand(
        null,
        MiscUtil.with(new FormattingOptions(), formattingOptions -> {
          formattingOptions.setTabSize(4);
          formattingOptions.setInsertSpaces(true);
        }));
    EditorUtil.differenceAfterAction(context.getPsiFile(), (copy) -> command.reformatPsiFile(context, copy));

//    var completionItemList = TestUtil.getCompletionListAtPosition(
//        getProject(), file, new Position(0, 1));
//
//    var expected = new HashSet<CompletionItem>();
//    expected.add(createCompletionItem("del", "", null, new ArrayList<>(), "del"));
//    expected.add(createCompletionItem("def", "", null, new ArrayList<>(), "def"));
//    expected.add(createCompletionItem("and", "", null, new ArrayList<>(), "and"));
//    expected.add(createCompletionItem("lambda", "", null, new ArrayList<>(), "lambda"));
//
//    Assertions.assertEquals(expected, new HashSet<>(completionItemList));
  }

  @Override
  protected String getTestDataPath() {
    return "test-data/formatting/formatting-project";
  }
}
