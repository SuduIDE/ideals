package org.rri.ideals.server.lsp;

import org.junit.Test;
import org.rri.ideals.server.DefaultTestFixture;
import org.rri.ideals.server.TestUtil;
import org.rri.ideals.server.references.engines.FindUsagesTestEngine;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Optional;

public class FindUsagesTest extends LspServerTestBase {
  @Override
  protected String getProjectRelativePath() {
    return "sandbox";
  }

  @Test
  public void findUsages() {
    try {
      final var dirPath =  getTestDataRoot().resolve("references/java/project-find-usages-integration");
      final var engine = new FindUsagesTestEngine(dirPath, server().getProject());
      final var definitionTests = engine.generateTests(new DefaultTestFixture(getProjectPath(), dirPath));
      for (final var test : definitionTests) {
        final var params = test.getParams();
        final var answer = test.getAnswer();

        final var future = server().getTextDocumentService().references(params);
        final var actual = Optional.ofNullable(TestUtil.getNonBlockingEdt(future, 50000));

        assertTrue(actual.isPresent());
        assertEquals(answer, actual.get());
      }
    } catch (IOException | RuntimeException e) {
      e.printStackTrace();
      fail();
    }
  }
}
