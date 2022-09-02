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
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.server.LspPath;
import org.rri.server.TestUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RunWith(JUnit4.class)
public class CompletionServiceTest extends BasePlatformTestCase {
  private static final Key<Boolean> ourShowTemplatesInTests = Key.create("ShowTemplatesInTests");
  private static final Key<Boolean> ourTemplateTesting = Key.create("TemplateTesting");
  private final Gson gson = new GsonBuilder().create();

  @Test
  public void testCompletionForKeywordsThatContainsLetterD() {
    final var file = myFixture.configureByFile("only_d_file.py");
    var completionItemList = getCompletionListAtPosition(
        file, new Position(0, 1));

    var expected = Set.of(
        CompletionServiceTestUtil.createCompletionItem(
            "del", "", null, new ArrayList<>(), "del", CompletionItemKind.Keyword
        ), CompletionServiceTestUtil.createCompletionItem(
            "def", "", null, new ArrayList<>(), "def", CompletionItemKind.Keyword
        ), CompletionServiceTestUtil.createCompletionItem(
            "and", "", null, new ArrayList<>(), "and", CompletionItemKind.Keyword
        ), CompletionServiceTestUtil.createCompletionItem(
            "lambda", "", null, new ArrayList<>(), "lambda", CompletionItemKind.Keyword)
    );
    Assert.assertNotNull(completionItemList);
    Assertions.assertEquals(expected,
        completionItemList.stream().map(CompletionServiceTestUtil::removeResolveInfo).collect(Collectors.toSet()));
  }

  @Test
  public void testCompletionForKeywordAndFunctionPython() {
    var expected = Set.of(CompletionServiceTestUtil.createCompletionItem(
        "for",
        "",
        null,
        new ArrayList<>(),
        "for",
        CompletionItemKind.Keyword
    ), CompletionServiceTestUtil.createCompletionItem(
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
        completionItemList.stream().map(CompletionServiceTestUtil::removeResolveInfo).collect(Collectors.toSet()));
  }

  @Test
  public void testCompletionForKeywordAndFunctionJava() {
    var expected = Set.of(CompletionServiceTestUtil.createCompletionItem(
        "formula",
        "()",
        "void",
        new ArrayList<>(),
        "formula",
        CompletionItemKind.Method
    ), CompletionServiceTestUtil.createCompletionItem(
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
        completionItemList.stream().map(CompletionServiceTestUtil::removeResolveInfo).collect(Collectors.toSet()));
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
    CompletionServiceTest.runWithTemplateFlags(
        () -> testResolve(
            """
                iter
                """,
            """
                for  in $0:
                \s\s\s\s
                """, new Position(0, 4),
            completionItem -> completionItem.getLabel().equals("iter"), PythonFileType.INSTANCE
        )
    );
  }
  @Test
  public void testPythonPostfixTemplate() {
    CompletionServiceTest.runWithTemplateFlags(
        () -> testResolve(
            """
                x.if
                """,
            """
                if x:
                \s\s\s\s$0
                """, new Position(0, 4),
            completionItem -> completionItem.getLabel().equals("if"), PythonFileType.INSTANCE)
    );
  }

  @Test
  public void testJavaLiveTemplate() {
    CompletionServiceTest.runWithTemplateFlags(
        () -> testResolve(
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
        ));
  }

  @Test
  public void testJavaPostfixTemplate() {
    CompletionServiceTest.runWithTemplateFlags(() -> testResolve(
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
        )
    );
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



  @Override
  protected String getTestDataPath() {
    return "test-data/completion/completion-project";
  }


  @NotNull
  private List<@NotNull CompletionItem> getCompletionListAtPosition(@NotNull PsiFile file,
                                                                     @NotNull Position position) {
    return getProject().getService(CompletionService.class).computeCompletions(
        LspPath.fromVirtualFile(file.getVirtualFile()), position, new TestUtil.DumbCancelChecker());
  }

  @NotNull
  private CompletionItem getResolvedCompletionItem(@NotNull CompletionItem unresolved) {
    return getProject()
        .getService(CompletionService.class)
        .applyCompletionResolve(unresolved, new TestUtil.DumbCancelChecker());
  }

  static private void runWithTemplateFlags(@NotNull Runnable action) {
    TestModeFlags.set(ourShowTemplatesInTests, true);
    TestModeFlags.set(ourTemplateTesting, true);
    try {
      action.run();
    } finally {
      TestModeFlags.set(ourShowTemplatesInTests, false);
      TestModeFlags.set(ourTemplateTesting, false);
    }
  }
}
