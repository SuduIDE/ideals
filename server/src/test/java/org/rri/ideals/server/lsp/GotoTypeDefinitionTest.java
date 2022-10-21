package org.rri.ideals.server.lsp;

import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.rri.ideals.server.TestUtil;
import org.rri.ideals.server.generator.IdeaOffsetPositionConverter;
import org.rri.ideals.server.references.generators.TypeDefinitionTestGenerator;

import java.util.Optional;

public class GotoTypeDefinitionTest extends LspServerTestWithEngineBase {
  @Override
  protected @NotNull String getMarkedTestProjectRelativePath() {
    return "references/java/project-type-definition-integration";
  }
  @Override
  protected String getProjectRelativePath() {
    return "sandbox";
  }

  @Test
  public void typeDefinition() {
    final var generator = new TypeDefinitionTestGenerator(getEngine(), new IdeaOffsetPositionConverter(server().getProject()));
    final var typeDefinitionTests = generator.generateTests();
    for (final var test : typeDefinitionTests) {
      final var params = test.params();
      final var answer = test.expected();

      final var future = server().getTextDocumentService().typeDefinition(params);
      final var actual = Optional.ofNullable(TestUtil.getNonBlockingEdt(future, 50000)).map(Either::getRight);

      assertTrue(actual.isPresent());
      assertEquals(answer, actual.get());
    }
  }
}
