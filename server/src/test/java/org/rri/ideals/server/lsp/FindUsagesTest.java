package org.rri.ideals.server.lsp;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.rri.ideals.server.TestUtil;
import org.rri.ideals.server.generator.IdeaOffsetPositionConverter;
import org.rri.ideals.server.references.generators.FindUsagesTestGenerator;

import java.util.HashSet;
import java.util.Optional;

public class FindUsagesTest extends LspServerTestWithEngineBase {
  @Override
  protected @NotNull String getMarkedTestProjectRelativePath() {
    return "references/java/project-find-usages-integration";
  }
  @Override
  protected String getProjectRelativePath() {
    return "sandbox";
  }

  @Test
  public void findUsages() {
    try {
      final var generator = new FindUsagesTestGenerator(getEngine(), new IdeaOffsetPositionConverter(server().getProject()));
      final var definitionTests = generator.generateTests();
      for (final var test : definitionTests) {
        final var params = test.params();
        final var answer = test.expected();

        final var future = server().getTextDocumentService().references(params);
        final var actual = Optional.ofNullable(TestUtil.getNonBlockingEdt(future, 50000));

        assertTrue(actual.isPresent());
        assertEquals(answer, new HashSet<>(actual.get()));
      }
    } catch (RuntimeException e) {
      e.printStackTrace();
      fail();
    }
  }
}
