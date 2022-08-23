package org.rri.server.completions;

import com.google.common.collect.Streams;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.server.LspPath;
import org.rri.server.TestUtil;
import org.rri.server.util.MiscUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(JUnit4.class)
public class CompletionServiceTest extends BasePlatformTestCase {
  @Test
  public void testCompletionForKeywordsThatContainsLetterD() {
    final var file = myFixture.configureByFile("only_d_file.py");
    var completionItemList = getCompletionListAtPosition(
        getProject(), file, new Position(0, 1));

    var expected = new HashSet<CompletionItem>();
    expected.add(
        createCompletionItem("del", "", null, new ArrayList<>(), CompletionItemKind.Keyword));
    expected.add(
        createCompletionItem("def", "", null, new ArrayList<>(), CompletionItemKind.Keyword));
    expected.add(
        createCompletionItem("and", "", null, new ArrayList<>(), CompletionItemKind.Keyword));
    expected.add(
        createCompletionItem("lambda", "", null, new ArrayList<>(), CompletionItemKind.Keyword));
    Streams.zip(completionItemList.stream(), expected.stream(),
        (completionItem, completionItem2) -> {
          completionItem2.setTextEdit(completionItem.getTextEdit());
          completionItem2.setAdditionalTextEdits(completionItem.getAdditionalTextEdits());
          return null;
        }
    );
    Assertions.assertEquals(expected, new HashSet<>(completionItemList));
  }

  @Test
  public void testCompletionForKeywordAndFunctionPython() {
    var expected = Set.of(
        createCompletionItem(
            "for",
            "",
            null,
            new ArrayList<>(),
            CompletionItemKind.Keyword),
        createCompletionItem(
            "formula",
            "(x)",
            null,
            new ArrayList<>(),
            CompletionItemKind.Function));

    final var file = myFixture.configureByFile("function_and_keyword.py");

    var completionItemList = getCompletionListAtPosition(
        getProject(), file, new Position(3, 3)
    );
    Assert.assertNotNull(completionItemList);
    Streams.zip(completionItemList.stream(), expected.stream(),
        (completionItem, completionItem2) -> {
          completionItem2.setTextEdit(completionItem.getTextEdit());
          return null;
        }
    );
    Assert.assertEquals(expected, new HashSet<>(completionItemList));
  }

  @Test
  public void testCompletionForKeywordAndFunctionJava() {
    var expected = new HashSet<CompletionItem>();
    expected.add(createCompletionItem(
        "formula",
        "()",
        "void",
        new ArrayList<>(),
        CompletionItemKind.Method
    ));
    expected.add(createCompletionItem(
        "for",
        "",
        null,
        new ArrayList<>(),
        CompletionItemKind.Keyword
    ));

    final var file = myFixture.configureByFile("function_and_keyword.java");

    var completionItemList = getCompletionListAtPosition(
        getProject(), file, new Position(2, 7)
    );
    Assert.assertNotNull(completionItemList);
    Streams.zip(completionItemList.stream(), expected.stream(),
        (completionItem, completionItem2) -> {
          completionItem2.setTextEdit(completionItem.getTextEdit());
          return null;
        }
    );
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
                                              @NotNull CompletionItemKind kind) {
    return MiscUtil.with(new CompletionItem(), item -> {
      item.setLabel(label);
      item.setLabelDetails(MiscUtil.with(new CompletionItemLabelDetails(), completionItemLabelDetails -> completionItemLabelDetails.setDetail(labelDetail)));
      item.setDetail(detail);
      item.setTags(completionItemTags);
      item.setKind(kind);
    });
  }

  @NotNull
  private static List<@NotNull CompletionItem> getCompletionListAtPosition(
      @NotNull Project project, @NotNull PsiFile file, @NotNull Position position) {
    return TestUtil.getNonBlockingEdt(project.getService(CompletionService.class).startCompletionCalculation(
        LspPath.fromVirtualFile(file.getVirtualFile()), position), 3000).getLeft();
  }
}
