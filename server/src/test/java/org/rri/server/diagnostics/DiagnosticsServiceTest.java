package org.rri.server.diagnostics;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.python.PythonFileType;
import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.server.LspContext;
import org.rri.server.LspPath;
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
      Assert.assertEquals(range(0, 1, 0, 2), it.getRange());
    });

    MiscUtil.with(diagnostics.get(1), it -> {
      Assert.assertEquals("Statement expected, found BAD_CHARACTER", it.getMessage());
      Assert.assertEquals(DiagnosticSeverity.Error, it.getSeverity());
      Assert.assertEquals(range(0, 3, 0, 4), it.getRange());
    });
  }

  private PublishDiagnosticsParams runAndGetDiagnostics(@NotNull PsiFile file) {
    getClient().resetDiagnosticsResult();
    getProject().getService(DiagnosticsService.class).launchDiagnostics(LspPath.fromVirtualFile(file.getVirtualFile()));
    return getClient().waitAndGetDiagnosticsPublished();
  }

  @SuppressWarnings("SameParameterValue")
  @NotNull
  private static Range range(int line1, int char1, int line2, int char2) {
    return new Range(new Position(line1, char1), new Position(line2, char2));
  }
}
