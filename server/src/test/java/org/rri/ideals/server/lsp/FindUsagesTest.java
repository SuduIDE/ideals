package org.rri.ideals.server.lsp;

import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.rri.ideals.server.TestUtil;
import org.rri.ideals.server.references.engines.FindUsagesTestEngine;

import java.nio.file.Path;
import java.util.Optional;

public class FindUsagesTest extends LspServerTestBase {
  @Override
  protected String getProjectRelativePath() {
    return "sandbox";
  }

  @Override
  protected @Nullable Path getTargetProjectPath() {
    return getTestDataRoot().resolve("references/java/project-find-usages-integration");
  }

  @Test
  public void findUsages() {
    try {
      final var engine = new FindUsagesTestEngine(server().getProject(), lexer.textsByFile, lexer.markersByFile);
      final var definitionTests = engine.processMarkers();
      for (final var test : definitionTests) {
        final var params = test.params();
        final var answer = test.answer();

        final var future = server().getTextDocumentService().references(params);
        final var actual = Optional.ofNullable(TestUtil.getNonBlockingEdt(future, 50000));

        assertTrue(actual.isPresent());
        assertEquals(answer, actual.get());
      }
    } catch (RuntimeException e) {
      e.printStackTrace();
      fail();
    }
  }
}
