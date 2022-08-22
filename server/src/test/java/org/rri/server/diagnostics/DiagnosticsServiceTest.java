package org.rri.server.diagnostics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.python.PythonFileType;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.server.LspContext;
import org.rri.server.LspPath;
import org.rri.server.TestUtil;
import org.rri.server.mocks.MockLanguageClient;
import org.rri.server.util.MiscUtil;

import java.util.List;

@RunWith(JUnit4.class)
public class DiagnosticsServiceTest extends BasePlatformTestCase {

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
  public void testSimpleSyntaxErrors() {
    final var file = myFixture.configureByText(PythonFileType.INSTANCE, "1 ! 2");

    final List<Diagnostic> diagnostics = runAndGetDiagnostics(file).getDiagnostics();

    Assert.assertEquals(2, diagnostics.size());

    MiscUtil.with(diagnostics.get(0), it -> {
      Assert.assertEquals("End of statement expected", it.getMessage());
      Assert.assertEquals(DiagnosticSeverity.Error, it.getSeverity());
      Assert.assertEquals(TestUtil.newRange(0, 1, 0, 2), it.getRange());
    });

    MiscUtil.with(diagnostics.get(1), it -> {
      Assert.assertEquals("Statement expected, found BAD_CHARACTER", it.getMessage());
      Assert.assertEquals(DiagnosticSeverity.Error, it.getSeverity());
      Assert.assertEquals(TestUtil.newRange(0, 3, 0, 4), it.getRange());
    });
  }

  @Test
  public void testQuickFix() {
    final var before = """
        class A {
           final int x = "a";
        }
        """;

    final var after = """
        class A {
           final java.lang.String x = "a";
        }
        """;

    final var actionTitle = "Change field 'x' type to 'String'";


    final var file = myFixture.configureByText("test.java", before);

    final var xVariableRange = TestUtil.newRange(1, 13, 1, 13);

    var path = LspPath.fromVirtualFile(file.getVirtualFile());

    final var diagnosticsService = getProject().getService(DiagnosticsService.class);

    runAndGetDiagnostics(file);

    final var codeActions = diagnosticsService.getCodeActions(path, xVariableRange);

    var action = codeActions.stream()
        .filter(it -> it.getTitle().equals(actionTitle))
        .findFirst()
        .orElseThrow(() -> new AssertionError("action not found"));

    Gson gson = new GsonBuilder().create();
    action.setData(gson.fromJson(gson.toJson(action.getData()), JsonObject.class));

    final var edit = diagnosticsService.applyCodeAction(action);

    Assert.assertEquals(after, TestUtil.applyEdits(file.getText(), edit.getChanges().get(path.toLspUri())));
  }

  private PublishDiagnosticsParams runAndGetDiagnostics(@NotNull PsiFile file) {
    getClient().resetDiagnosticsResult();
    getProject().getService(DiagnosticsService.class).launchDiagnostics(LspPath.fromVirtualFile(file.getVirtualFile()));
    return getClient().waitAndGetDiagnosticsPublished();
  }
}
