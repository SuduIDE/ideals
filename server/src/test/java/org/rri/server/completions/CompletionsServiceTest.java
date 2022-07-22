package org.rri.server.completions;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemLabelDetails;
import org.eclipse.lsp4j.Position;
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
public class CompletionsServiceTest extends BasePlatformTestCase {

  @Test
  public void testCompletionItemsAreAllDifferent() {
    final var file = myFixture.configureByFile(getEmptyPythonFilePath());
    var completionItemList =
            TestUtil.getCompletionListAtPosition(getProject(), file, new Position(0, 0));
    Assertions.assertEquals(completionItemList.size(), (new HashSet<>(completionItemList)).size());
  }

  @Test
  public void testCompletionForKeywords() {
    final var file = myFixture.configureByFile(getEmptyPythonFilePath());
    var completionItemList = TestUtil.getCompletionListAtPosition(
            getProject(), file, new Position(0, 0)
    );
    Assertions.assertEquals(25, completionItemList.size());
    completionItemList.forEach(completionItem -> {
      Assertions.assertEquals(completionItem.getLabel(), completionItem.getInsertText());
      Assertions.assertEquals("", completionItem.getLabelDetails().getDetail());
      Assertions.assertNull(completionItem.getLabelDetails().getDescription());
      Assertions.assertEquals(0, completionItem.getTags().size());
    });
  }

  @Test
  public void testCompletionForKeywordAndFunction() {
    var correctSet = new HashSet<>();
    correctSet.add(MiscUtil.with(new CompletionItem(), item -> {
      item.setLabel("for");
      item.setLabelDetails(MiscUtil.with(new CompletionItemLabelDetails(), completionItemLabelDetails -> {
        completionItemLabelDetails.setDetail("");
        completionItemLabelDetails.setDescription(null);
      }));
      item.setTags(new ArrayList<>());
      item.setInsertText("for");
    }));
    correctSet.add(MiscUtil.with(new CompletionItem(), item -> {
      item.setLabel("formula");
      item.setLabelDetails(MiscUtil.with(new CompletionItemLabelDetails(), completionItemLabelDetails -> {
        completionItemLabelDetails.setDetail("(x)");
        completionItemLabelDetails.setDescription(null);
      }));
      item.setTags(new ArrayList<>());
      item.setInsertText("formula");
    }));

    final var file = myFixture.configureByFile("function_and_keyword.py");

    var completionItemList = TestUtil.getCompletionListAtPosition(
            getProject(), file, new Position(3, 3)
    );
    Assert.assertNotNull(completionItemList);

    Assert.assertEquals(correctSet, new HashSet<>(completionItemList));
  }

  private String getEmptyPythonFilePath() {
    return "empty_file.py";
  }

  @Override
  protected String getTestDataPath() {
    return "test-data/completion-project";
  }
}
