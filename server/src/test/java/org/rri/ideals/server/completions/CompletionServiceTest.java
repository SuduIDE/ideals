package org.rri.ideals.server.completions;

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
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.ideals.server.IdeaTestFixture;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.TestUtil;
import org.rri.ideals.server.util.MiscUtil;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@RunWith(JUnit4.class)
public class CompletionServiceTest extends BasePlatformTestCase {
  private static final Key<Boolean> ourShowTemplatesInTests = Key.create("ShowTemplatesInTests");
  private static final Key<Boolean> ourTemplateTesting = Key.create("TemplateTesting");
  private final Gson gson = new GsonBuilder().create();

  @Override
  protected String getTestDataPath() {
    return "test-data/completion";
  }

  @Test
  public void testCompletionForStaticImport() {
    final var dirPath = Paths.get(getTestDataPath(), "import-static-project");
    testWithEngine(new CompletionTestParams(dirPath, completionItem -> true, null, null));
  }

  @Test
  public void testTemplateCompletion() {
    final var dirPath = Paths.get(getTestDataPath(), "template-main-project");
    runWithTemplateFlags(() -> testWithEngine(new CompletionTestParams(dirPath, completionItem -> true, null, null)));
  }

  private record CompletionTestParams(@NotNull Path dirPath,
                                      @Nullable Predicate<? super CompletionItem> finder,
                                      @Nullable MarkupContent documentation,
                                      @Nullable Set<CompletionItem> expectedItems) {
  }

  private void testWithEngine(@NotNull CompletionTestParams completionTestParams) {
    try {
      final var engine = new CompletionTestEngine(completionTestParams.dirPath, getProject());
      final var completionTest = engine.generateTests(new IdeaTestFixture(myFixture));
      final var test = completionTest.get(0);
      final var params = test.getParams();
      final var expectedText = test.getAnswer();
      var cs = getProject().getService(CompletionService.class);
      var completionItems = cs.computeCompletions(
          LspPath.fromLspUri(params.getTextDocument().getUri()), params.getPosition(),
          new TestUtil.DumbCancelChecker());
      if (completionTestParams.finder != null) {
        var compItem = completionItems.stream().filter(completionTestParams.finder).findFirst().orElseThrow();
        compItem.setData(gson.fromJson(gson.toJson(compItem.getData()), JsonObject.class));
        var resolved = cs.resolveCompletion(compItem, new TestUtil.DumbCancelChecker());
        assertNotNull(expectedText);
        assertNotNull(test.getSourceText());
        var allEdits = new ArrayList<TextEdit>();
        allEdits.add(resolved.getTextEdit().getLeft());
        allEdits.addAll(resolved.getAdditionalTextEdits());
        assertEquals(expectedText, TestUtil.applyEdits(test.getSourceText(), allEdits));

        if (completionTestParams.documentation != null) {
          assertEquals(completionTestParams.documentation, compItem.getDocumentation().getRight());
        }
      }
      if (completionTestParams.expectedItems != null) {
        assertEquals(completionTestParams.expectedItems(),
            completionItems.stream().map(CompletionServiceTestUtil::removeResolveInfo).collect(Collectors.toSet()));
      }
    } catch (Exception e) {
      throw MiscUtil.wrap(e);
    }
  }

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


    final var dirPath = Paths.get(getTestDataPath(), "python-function-and-keyword-project");
    testWithEngine(new CompletionTestParams(dirPath, null, null, expected));
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
                ""\"
                :param x: real human bean
                :return: actual real hero
                ""\"
                foo(x)
                    
            foo
            """,
        """
            def foo(x):
                ""\"
                :param x: real human bean
                :return: actual real hero
                ""\"
                foo(x)
                 
            foo($0)
            """,
        new Position(7, 3),
        completionItem -> completionItem.getLabel().equals("foo"), PythonFileType.INSTANCE,
        """
            [aaa](psi_element://#module#aaa)\s\s
            def **foo**(x: Any) -> None
                        
            Unittest placeholder
                        
            Params:
                        
            `x` \u2013 real human bean
                        
            Returns:
                        
            actual real hero"""
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
        completionItem -> completionItem.getLabel().equals("foo"), PythonFileType.INSTANCE,
        """
            [aaa](psi_element://#module#aaa)\s\s
            def **foo**() -> None"""
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
            completionItem -> completionItem.getLabel().equals("iter"), PythonFileType.INSTANCE,
            """
                for $VAR$ in $ITERABLE$: $END$
                                
                Iterate (for ... in ...)"""
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
            completionItem -> completionItem.getLabel().equals("if"), PythonFileType.INSTANCE,
            null)
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
            completionItem -> completionItem.getLabel().equals("fori"), JavaFileType.INSTANCE,
            """
                for(int $INDEX$ = 0; $INDEX$ < $LIMIT$; $INDEX$++) { $END$ }
                                
                Create iteration loop"""
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
            new Position(2, 11),
            completionItem -> completionItem.getLabel().equals("lambda"), JavaFileType.INSTANCE, null
        )
    );
  }

  @Test
  public void testOwnJavadoc() {
    CompletionServiceTest.runWithTemplateFlags(() -> testResolve(
        """
            class Javadoc {
                /**
                 * this is a test function
                 * @param b a test parameter
                 * @return 1\s
                 */
                int test(boolean b) {
                    test
                    return 1;
                }
            }""",
        """
            class Javadoc {
                /**
                 * this is a test function
                 * @param b a test parameter
                 * @return 1\s
                 */
                int test(boolean b) {
                    test($0)
                    return 1;
                }
            }""",
        new Position(7, 12),
        completionItem -> completionItem.getLabel().equals("test"),
        JavaFileType.INSTANCE,
        """
            \s[`Javadoc`](psi_element://Javadoc)
                        
            int test(\s\s
             boolean b\s\s
            )
                        
            this is a test function
                        
            Params:
                        
            `b` \u2013 a test parameter
                        
            Returns:
                        
            1"""
        )
    );
  }

  private void testResolve(@NotNull String originalText, @NotNull String expectedText,
                           @NotNull Position position, @NotNull Function<CompletionItem, Boolean> searchFunction,
                           @NotNull FileType fileType, @Nullable String expectedDoc) {
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
    if (resolvedCompletionItem.getDocumentation() != null) {
      Assert.assertEquals(expectedDoc, resolvedCompletionItem.getDocumentation().getRight().getValue());
    } else {
      Assert.assertNull(expectedDoc);
    }
    Assert.assertEquals(expectedText, TestUtil.applyEdits(originalText, allEdits));
  }

  @Test
  public void testCompletionCancellation() {
    var cancelChecker = new AlwaysTrueCancelChecker();

    var psiFile = myFixture.configureByText(
        PythonFileType.INSTANCE,
        "");
    Assertions.assertThrows(
        CancellationException.class,
        () -> getCompletionListAtPosition(psiFile, new Position(0, 0), cancelChecker));
  }

  @Test
  public void testResolveCancellation() {

    var psiFile = myFixture.configureByText(
        JavaFileType.INSTANCE,
        """
            class Test {
            \s\s\s\s
            }
            """);

    var completionList = Assertions.assertDoesNotThrow(
        () -> getCompletionListAtPosition(psiFile, new Position(1, 3)));

    var cancelChecker = new AlwaysTrueCancelChecker();

    var targetCompletionItem =
        completionList.stream()
            .filter(completionItem -> completionItem.getLabel().equals("public"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("completion item not found"));

    targetCompletionItem.setData(gson.fromJson(
        gson.toJson(targetCompletionItem.getData()),
        JsonObject.class));
    Assertions.assertThrows(CancellationException.class,
        () -> getResolvedCompletionItem(targetCompletionItem, cancelChecker));
  }

  private static class AlwaysTrueCancelChecker implements CancelChecker {
    @Override
    public void checkCanceled() {
      throw new CancellationException();
    }
  }




  @NotNull
  private List<@NotNull CompletionItem> getCompletionListAtPosition(@NotNull PsiFile file,
                                                                    @NotNull Position position) {
    return getCompletionListAtPosition(file, position, new TestUtil.DumbCancelChecker());
  }

  @NotNull
  private List<@NotNull CompletionItem> getCompletionListAtPosition(@NotNull PsiFile file,
                                                                    @NotNull Position position,
                                                                    @NotNull CancelChecker cancelChecker) {
    return getProject().getService(CompletionService.class).computeCompletions(
        LspPath.fromVirtualFile(file.getVirtualFile()), position, cancelChecker);
  }

  @NotNull
  private CompletionItem getResolvedCompletionItem(@NotNull CompletionItem unresolved) {
    return getResolvedCompletionItem(unresolved, new TestUtil.DumbCancelChecker());
  }

  @NotNull
  private CompletionItem getResolvedCompletionItem(@NotNull CompletionItem unresolved,
                                                   @NotNull CancelChecker cancelChecker) {
    return getProject()
        .getService(CompletionService.class)
        .resolveCompletion(unresolved, cancelChecker);
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
