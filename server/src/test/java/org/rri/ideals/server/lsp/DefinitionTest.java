package org.rri.ideals.server.lsp;

import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.Test;
import org.rri.ideals.server.DefaultTestFixture;
import org.rri.ideals.server.TestUtil;
import org.rri.ideals.server.references.ReferencesTestEngine;

import java.io.IOException;
import java.util.Optional;

public class DefinitionTest extends LspServerTestBase {

  @Override
  protected String getProjectRelativePath() {
    return "references/java/project-definition-integration";
  }

  @Test
  public void definition() {
    try {
      final var sandboxPath =  getTestDataRoot().resolve("sandbox");
      final var engine = new ReferencesTestEngine(getProjectPath(), server().getProject());
      final var definitionTests = engine.generateTests(new DefaultTestFixture(sandboxPath));
      for (final var test : definitionTests) {
        final var params = test.getParams();
        final var answer = test.getAnswer();

        final var future = server().getTextDocumentService().definition(params);
        final var actual = Optional.ofNullable(TestUtil.getNonBlockingEdt(future, 50000)).map(Either::getRight);

        assertTrue(actual.isPresent());
        assertEquals(answer, actual.get());
      }
    } catch (IOException | RuntimeException e) {
      e.printStackTrace();
      fail();
    }
  }
}
