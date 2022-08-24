package org.rri.server.completions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.python.PythonFileType;
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
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RunWith(JUnit4.class)
public class CompletionServiceTest extends BasePlatformTestCase {
  private final Gson gson = new GsonBuilder().create();
  private static final Key<Boolean> ourShowTemplatesInTests = Key.create("ShowTemplatesInTests");
  private static final Key<Boolean> ourTemplateTesting = Key.create("TemplateTesting");

  @Test
  public void testCompletionForKeywordsThatContainsLetterD() {
    final var file = myFixture.configureByFile("only_d_file.py");
    var completionItemList = getCompletionListAtPosition(
        file, new Position(0, 1));

    var expected = Set.of(
        createCompletionItem(
            "del", "", null, new ArrayList<>(), "del", CompletionItemKind.Keyword
        ), createCompletionItem(
            "def", "", null, new ArrayList<>(), "def", CompletionItemKind.Keyword
        ), createCompletionItem(
            "and", "", null, new ArrayList<>(), "and", CompletionItemKind.Keyword
        ), createCompletionItem(
            "lambda", "", null, new ArrayList<>(), "lambda", CompletionItemKind.Keyword)
    );
    Assert.assertNotNull(completionItemList);
    Assertions.assertEquals(expected,
        completionItemList.stream().map(CompletionServiceTest::removeResolveInfo).collect(Collectors.toSet()));
  }

  @Test
  public void testCompletionForKeywordAndFunctionPython() {
    var expected = Set.of(createCompletionItem(
        "for",
        "",
        null,
        new ArrayList<>(),
        "for",
        CompletionItemKind.Keyword
    ), createCompletionItem(
        "formula",
        "(x)",
        null,
        new ArrayList<>(),
        "formula",
        CompletionItemKind.Function));

    final var file = myFixture.configureByFile("function_and_keyword.py");

    var completionItemList = getCompletionListAtPosition(
        file, new Position(3, 3)
    );
    Assert.assertNotNull(completionItemList);
    Assert.assertEquals(expected,
        completionItemList.stream().map(CompletionServiceTest::removeResolveInfo).collect(Collectors.toSet()));
  }

  @Test
  public void testCompletionForKeywordAndFunctionJava() {
    var expected = Set.of(createCompletionItem(
        "formula",
        "()",
        "void",
        new ArrayList<>(),
        "formula",
        CompletionItemKind.Method
    ), createCompletionItem(
        "for",
        "",
        null,
        new ArrayList<>(),
        "for",
        CompletionItemKind.Keyword
    ));

    final var file = myFixture.configureByFile("function_and_keyword.java");

    var completionItemList = getCompletionListAtPosition(
        file, new Position(2, 7)
    );
    Assert.assertNotNull(completionItemList);
    Assert.assertEquals(expected,
        completionItemList.stream().map(CompletionServiceTest::removeResolveInfo).collect(Collectors.toSet()));
  }

  @Test
  public void testCompletionResolveFunctionsWithParameters() {
    testResolve(
        """
            def foo(x):
                foo(x)
                    
            foo
            """,
        """
            def foo(x):
                foo(x)
                 
            foo($0)
            """,
        new Position(3, 3),
        completionItem -> completionItem.getLabel().equals("foo"), PythonFileType.INSTANCE
    );
  }

  @Test
  public void testCompletionResolveFunctionsWithoutParameters() {
    testResolve(
        """
            def foo():
                foo()
                    
            foo
            """,
        """
            def foo():
                foo()
                 
            foo()$0
            """,
        new Position(3, 3),
        completionItem -> completionItem.getLabel().equals("foo"), PythonFileType.INSTANCE
    );
  }

  @Test
  public void testPythonLiveTemplate() {
    testResolveWithTemplates(
        """
            iter
            """,
        """
            for  in $0:
            \s\s\s\s
            """, new Position(0, 4),
        completionItem -> completionItem.getLabel().equals("iter"), PythonFileType.INSTANCE
    );
  }
  @Test
  public void testPythonPostfixTemplate() {
    testResolveWithTemplates(
        """
            x.if
            """,
        """
            if x:
            \s\s\s\s$0
            """, new Position(0, 4),
        completionItem -> completionItem.getLabel().equals("if"), PythonFileType.INSTANCE
    );
  }

  @Test
  public void testJavaLiveTemplate() {
    testResolveWithTemplates(
        """
            class Templates {             
                void test() {
                    fori
                }
            }""",
        """
            class Templates {
                void test() {
                    for (int i$0 = 0; i < ; i++) {
                    \s\s\s\s
                    }
                }
            }""",
        new Position(2, 12),
        completionItem -> completionItem.getLabel().equals("fori"), JavaFileType.INSTANCE
    );
  }

  @Test
  public void testJavaPostfixTemplate() {
    testResolveWithTemplates(
        """
            class Templates {             
                void test() {
                    x.l
                }
            }""",
        """
            class Templates {
                void test() {
                    () -> x$0
                }
            }""",
        new Position(2, 12),
        completionItem -> completionItem.getLabel().equals("lambda"), JavaFileType.INSTANCE
    );
  }

  private void testResolveWithTemplates(String originalText, String expectedText, Position position,
                                        Function<CompletionItem, Boolean> searchFunction,
                                        FileType fileType) {
    TestModeFlags.set(ourShowTemplatesInTests, true);
    TestModeFlags.set(ourTemplateTesting, true);
    try {
      testResolve(originalText, expectedText, position, searchFunction, fileType);
    } finally {
      TestModeFlags.set(ourShowTemplatesInTests, false);
      TestModeFlags.set(ourTemplateTesting, false);
    }
  }

  private void testResolve(String originalText, String expectedText, Position position,
                           Function<CompletionItem, Boolean> searchFunction, FileType fileType) {
    var psiFile = myFixture.configureByText(
        fileType,
        originalText);
    var completionList = getCompletionListAtPosition(
        psiFile, position);
    var targetCompletionItem =
        completionList.stream()
            .filter(searchFunction::apply)
            .findFirst()
            .orElseThrow(() -> new AssertionError("completion item not found"));

    targetCompletionItem.setData(gson.fromJson(
        gson.toJson(targetCompletionItem.getData()),
        JsonObject.class));

    var resolvedCompletionItem = getResolvedCompletionItem(targetCompletionItem);
    var allEdits = new ArrayList<>(resolvedCompletionItem.getAdditionalTextEdits());
    allEdits.add(resolvedCompletionItem.getTextEdit().getLeft());
    Assert.assertEquals(expectedText, TestUtil.applyEdits(originalText, allEdits));
  }


  private static CompletionItem removeResolveInfo(CompletionItem completionItem) {
    completionItem.setTextEdit(null);
    completionItem.setData(null);
    return completionItem;
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
                                              @NotNull String filterText,
                                              @Nullable CompletionItemKind kind) {
    return MiscUtil.with(new CompletionItem(), item -> {
      item.setLabel(label);
      item.setLabelDetails(MiscUtil.with(new CompletionItemLabelDetails(), completionItemLabelDetails -> completionItemLabelDetails.setDetail(labelDetail)));
      item.setDetail(detail);
      item.setTags(completionItemTags);
      item.setInsertTextFormat(InsertTextFormat.Snippet);
      item.setFilterText(filterText);
      item.setInsertTextMode(InsertTextMode.AsIs);
      item.setKind(kind);
    });
  }

  @NotNull
  private List<@NotNull CompletionItem> getCompletionListAtPosition(@NotNull PsiFile file, @NotNull Position position) {
    return TestUtil.getNonBlockingEdt(getProject().getService(CompletionService.class).startCompletionCalculation(
        LspPath.fromVirtualFile(file.getVirtualFile()), position), 3000).getLeft();
  }

  @NotNull
  private CompletionItem getResolvedCompletionItem(@NotNull CompletionItem unresolved) {
    return TestUtil.getNonBlockingEdt(getProject().getService(CompletionService.class)
        .startCompletionResolveCalculation(unresolved), 3000);
  }
}
