package org.rri.ideals.server.lsp;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.rri.ideals.server.TestUtil;
import org.rri.ideals.server.generator.IdeaOffsetPositionConverter;
import org.rri.ideals.server.references.generators.TypeDefinitionTestGenerator;

import java.util.HashSet;

public class GotoTypeDefinitionTest extends LspServerTestWithEngineBase {
  @Override
  protected @NotNull String getTestDataRelativePath() {
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
      final var actual = TestUtil.getNonBlockingEdt(future, 50000);

      assertEquals(answer, new HashSet<>(actual.getRight()));
    }
  }
}
