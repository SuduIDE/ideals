package org.rri.ideals.server.lsp;

import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.Test;
import org.rri.ideals.server.TestUtil;
import org.rri.ideals.server.engine.DefaultTestFixture;
import org.rri.ideals.server.engine.TestEngine;
import org.rri.ideals.server.generator.IdeaOffsetPositionConverter;
import org.rri.ideals.server.references.generators.TypeDefinitionTestGenerator;

import java.io.IOException;
import java.util.Optional;

public class GotoTypeDefinitionTest extends LspServerTestBase {

  @Override
  protected String getProjectRelativePath() {
    return "sandbox";
  }

  @Test
  public void typeDefinition() {
    try {
      final var dirPath =  getTestDataRoot().resolve("references/java/project-type-definition-integration");
      final var engine = new TestEngine(dirPath);
      engine.initSandbox(new DefaultTestFixture(getProjectPath(), dirPath));
      final var generator = new TypeDefinitionTestGenerator(engine.textsByFile, engine.markersByFile, new IdeaOffsetPositionConverter(server().getProject()));
      final var typeDefinitionTests = generator.generateTests();
      for (final var test : typeDefinitionTests) {
        final var params = test.params();
        final var answer = test.answer();

        final var future = server().getTextDocumentService().typeDefinition(params);
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
