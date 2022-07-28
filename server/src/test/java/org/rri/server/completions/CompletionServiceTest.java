package org.rri.server.completions;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemLabelDetails;
import org.eclipse.lsp4j.CompletionItemTag;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.server.TestUtil;
import org.rri.server.util.MiscUtil;

import java.util.ArrayList;
import java.util.HashSet;

@RunWith(JUnit4.class)
public class CompletionServiceTest extends BasePlatformTestCase {
  @Test
  public void testCompletionForKeywordsThatContainsLetterD() {
    final var file = myFixture.configureByFile("only_d_file.py");
    var completionItemList = TestUtil.getCompletionListAtPosition(
        getProject(), file, new Position(0, 1));

    var expected = new HashSet<CompletionItem>();
    expected.add(createCompletionItem("del", "", null, new ArrayList<>(), "del"));
    expected.add(createCompletionItem("def", "", null, new ArrayList<>(), "def"));
    expected.add(createCompletionItem("and", "", null, new ArrayList<>(), "and"));
    expected.add(createCompletionItem("lambda", "", null, new ArrayList<>(), "lambda"));

    Assertions.assertEquals(expected, new HashSet<>(completionItemList));
  }

  @Test
  public void testCompletionForKeywordAndFunctionPython() {
    var expected = new HashSet<CompletionItem>();
    expected.add(createCompletionItem("for", "", null, new ArrayList<>(), "for"));
    expected.add(createCompletionItem("formula", "(x)", null, new ArrayList<>(), "formula"));
    final var file = myFixture.configureByFile("function_and_keyword.py");

    var completionItemList = TestUtil.getCompletionListAtPosition(
        getProject(), file, new Position(3, 3)
    );
    Assert.assertNotNull(completionItemList);
    Assert.assertEquals(expected, new HashSet<>(completionItemList));
  }

  @Test
  public void testCompletionForKeywordAndFunctionJava() {
    var expected = new HashSet<CompletionItem>();
    expected.add(createCompletionItem("formula", "()", "void", new ArrayList<>(), "formula"));
    expected.add(createCompletionItem("for", "", null, new ArrayList<>(), "for"));

    final var file = myFixture.configureByFile("function_and_keyword.java");

    var completionItemList = TestUtil.getCompletionListAtPosition(
        getProject(), file, new Position(2, 7)
    );
    Assert.assertNotNull(completionItemList);
    Assert.assertEquals(expected, new HashSet<>(completionItemList));
  }

  @Override
  protected String getTestDataPath() {
    return "test-data/completion/completion-project";
  }

  @SuppressWarnings("SameParameterValue")
  @NotNull
  private CompletionItem createCompletionItem(@NotNull String label,
                                              @NotNull String labelDetail,
                                              @Nullable String detail,
                                              @NotNull ArrayList<@NotNull CompletionItemTag> completionItemTags,
                                              @NotNull String insertText) {
    return MiscUtil.with(new CompletionItem(), item -> {
      item.setLabel(label);
      item.setLabelDetails(MiscUtil.with(new CompletionItemLabelDetails(), completionItemLabelDetails -> completionItemLabelDetails.setDetail(labelDetail)));
      item.setDetail(detail);
      item.setTags(completionItemTags);
      item.setInsertText(insertText);
    });
  }
}
