package org.rri.ideals.server.references;

import org.eclipse.lsp4j.Location;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.ideals.server.IdeaTestFixture;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.TestUtil;
import org.rri.ideals.server.references.engines.FindUsagesTestEngine;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Optional;

@RunWith(JUnit4.class)
public class FindUsagesCommandTest extends ReferencesCommandTestBase {
  @Test
  public void testFindUsagesJava() {
    final var dirPath = Paths.get(getTestDataPath(), "java/project-find-usages");
    checkDefinitionByDirectory(dirPath);
  }

  @Test
  public void testFindUsagesPython() {
    final var dirPath = Paths.get(getTestDataPath(), "python/project-find-usages");
    checkDefinitionByDirectory(dirPath);
  }

  private void checkDefinitionByDirectory(Path dirPath) {
    try {
      final var engine = new FindUsagesTestEngine(dirPath, getProject());
      final var referencesTests = engine.generateTests(new IdeaTestFixture(myFixture));
      for (final var test : referencesTests) {
        final var params = test.getParams();
        final var answer = test.getAnswer();

        final var path = LspPath.fromLspUri(params.getTextDocument().getUri());
        final var future = new FindUsagesCommand(params.getPosition()).runAsync(getProject(), path);
        final var actual = Optional.ofNullable(TestUtil.getNonBlockingEdt(future, 50000));

        assertTrue(actual.isPresent());
        assertEquals(new HashSet<Location>(answer), new HashSet<>(actual.get()));
      }
    } catch (IOException | RuntimeException e) {
      System.err.println(e instanceof IOException ? "IOException:" : "RuntimeException");
      e.printStackTrace();
      fail();
    }
  }
}
