package org.rri.ideals.server.signature;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.python.PythonFileType;
import org.eclipse.lsp4j.Position;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.TestUtil;

import java.util.List;

@RunWith(JUnit4.class)
public class SignatureHelpServiceTest extends BasePlatformTestCase {

  @Test
  @Ignore
  public void testPythonFunctionWithNoParameters() {
    final var text = """
    def foo():
        return 42
   
    foo()
    """;

    final var file = myFixture.configureByText(PythonFileType.INSTANCE, text);
    var signatureHelpService = getProject().getService(SignatureHelpService.class);
    // it doesn't compute because by BasePlatformTestCase this method always runs on EDT
    var signatureHelp =
        signatureHelpService.computeSignatureHelp(LspPath.fromVirtualFile(file.getVirtualFile()),
        new Position(3, 4), new TestUtil.DumbCancelChecker());
    assertNotNull(signatureHelp);
    assertEquals(List.of(), signatureHelp.getSignatures());
  }
}
