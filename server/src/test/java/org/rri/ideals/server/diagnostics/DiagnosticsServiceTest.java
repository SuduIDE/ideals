package org.rri.ideals.server.diagnostics;

import com.jetbrains.python.PythonFileType;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.TestUtil;
import org.rri.ideals.server.util.MiscUtil;

import java.util.List;
import java.util.stream.Stream;

@RunWith(JUnit4.class)
public class DiagnosticsServiceTest extends DiagnosticsTestBase {

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
  public void testKotlinErrors() {
    final var text = """
    fun main(): Unit {
      Test.main(args)
    }
    """;

    final var file = myFixture.configureByText("test.kt", text);

    final List<Diagnostic> diagnostics = runAndGetDiagnostics(file).getDiagnostics();

    Assert.assertEquals(2, diagnostics.size());

    MiscUtil.with(diagnostics.get(0), it -> {
      Assert.assertEquals("[UNRESOLVED_REFERENCE] Unresolved reference: Test", it.getMessage());
      Assert.assertEquals(DiagnosticSeverity.Error, it.getSeverity());
      Assert.assertEquals(TestUtil.newRange(1, 2, 1, 6), it.getRange());
    });

    MiscUtil.with(diagnostics.get(1), it -> {
      Assert.assertEquals("[UNRESOLVED_REFERENCE] Unresolved reference: args", it.getMessage());
      Assert.assertEquals(DiagnosticSeverity.Error, it.getSeverity());
      Assert.assertEquals(TestUtil.newRange(1, 12, 1, 16), it.getRange());
    });
  }

  @Test
  public void testGetQuickFixes() {

    var expected = Stream.of(
        "Wrap using 'java.util.Optional'",
        "Wrap using 'null()'",
        "Adapt using call or new object",
        "<html>Migrate 'x' type to 'String'</html>",
        "Change field 'x' type to 'String'"
    ).sorted().toList();

    final var file = myFixture.configureByText("test.java", """
        class A {
           final int x = "a";
        }
        """);

    final var xVariableRange = TestUtil.newRange(1, 13, 1, 13);

    var path = LspPath.fromVirtualFile(file.getVirtualFile());

    final var diagnosticsService = getProject().getService(DiagnosticsService.class);

    Assert.assertTrue(diagnosticsService.getQuickFixes(path, xVariableRange).isEmpty());

    runAndGetDiagnostics(file);

    final var quickFixes = diagnosticsService.getQuickFixes(path, xVariableRange);

    Assert.assertEquals(expected, quickFixes.stream().map(it -> it.getAction().getText()).sorted().toList());
  }
}
