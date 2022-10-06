package org.rri.ideals.server.references;

import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.ideals.server.IdeaTestFixture;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.TestUtil;
import org.rri.ideals.server.references.engines.TypeDefinitionTestEngine;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@RunWith(JUnit4.class)
public class TypeDefinitionCommandTest extends ReferencesCommandTestBase {
  @Test
  public void testTypeDefinitionJava() {
    final var dirPath = Paths.get(getTestDataPath(), "java/project-type-definition");
    checkTypeDefinitionByDirectory(dirPath);
  }

  @Test
  public void testTypeDefinitionPython() {
    final var dirPath = Paths.get(getTestDataPath(), "python/project-type-definition");
    checkTypeDefinitionByDirectory(dirPath);
  }

  private void checkTypeDefinitionByDirectory(Path dirPath) {
    try {
      final var engine = new TypeDefinitionTestEngine(dirPath, getProject());
      final var referencesTests = engine.generateTests(new IdeaTestFixture(myFixture));
      for (final var test : referencesTests) {
        final var params = test.getParams();
        final var answer = test.getAnswer();

        final var path = LspPath.fromLspUri(params.getTextDocument().getUri());
        final var future = new FindTypeDefinitionCommand(params.getPosition()).runAsync(getProject(), path);
        final var actual =
            Optional.ofNullable(TestUtil.getNonBlockingEdt(future, 50000)).map(Either::getRight);

        assertTrue(actual.isPresent());
        assertEquals(answer, actual.get());
      }
    } catch (IOException | RuntimeException e) {
      System.err.println(e instanceof IOException ? "IOException:" : "RuntimeException");
      System.err.println(e.getMessage());
      fail();
    }
  }
}
