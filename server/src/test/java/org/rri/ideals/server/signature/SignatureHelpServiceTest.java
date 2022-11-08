package org.rri.ideals.server.signature;

import com.intellij.codeInsight.hint.ParameterInfoListener;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.python.PythonFileType;
import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SignatureInformation;
import org.eclipse.lsp4j.jsonrpc.messages.Tuple;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.TestUtil;
import org.rri.ideals.server.util.MiscUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(JUnit4.class)
public class SignatureHelpServiceTest extends BasePlatformTestCase {

  @Test
  public void testPythonFunctionWithNoParameters() {
    final var text = """
        def foo():
            return 42
           
        foo()
        """;

    final var file = myFixture.configureByText(PythonFileType.INSTANCE, text);
    var signatureHelpService = getProject().getService(SignatureHelpService.class);
    signatureHelpService.setEdtFlushRunnable(defaultFlushRunnable());

    var signatureHelp =
        signatureHelpService.computeSignatureHelp(LspPath.fromVirtualFile(file.getVirtualFile()),
            new Position(3, 4), new TestUtil.DumbCancelChecker());
    assertNotNull(signatureHelp);
    var expected = Set.of(new SignatureInformation(
              "<no parameters>", (String) null, List.of(MiscUtil.with(new ParameterInformation(),
                  pi -> pi.setLabel(Tuple.two(0, 15))))));

    assertEquals(expected, new HashSet<>(signatureHelp.getSignatures()));
  }

  private static Runnable defaultFlushRunnable() {
    return () -> TestUtil.waitInEdtFor(() -> {
          for (var listener : ParameterInfoListener.EP_NAME.getExtensionList()) {
            if (listener instanceof MyParameterInfoListener myListener) {
              return !myListener.queue.isEmpty();
            }
          }
          return false;
        },
        5000);
  }
}
