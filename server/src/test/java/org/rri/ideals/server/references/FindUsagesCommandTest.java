package org.rri.ideals.server.references;

import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.TestUtil;
import org.rri.ideals.server.engine.IdeaTestFixture;
import org.rri.ideals.server.engine.TestEngine;
import org.rri.ideals.server.generator.IdeaOffsetPositionConverter;
import org.rri.ideals.server.references.generators.FindUsagesTestGenerator;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Optional;

@RunWith(JUnit4.class)
public class FindUsagesCommandTest extends ReferencesCommandTestBase<FindUsagesTestGenerator> {
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
      final var engine = new TestEngine(dirPath);
      engine.initSandbox(new IdeaTestFixture(myFixture));
      final var generator = new FindUsagesTestGenerator(engine.textsByFile, engine.markersByFile, new IdeaOffsetPositionConverter(getProject()));
      final var referencesTests = generator.generateTests();
      for (final var test : referencesTests) {
        final var params = test.params();
        final var answer = test.answer();

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

  @Override
  protected @NotNull FindUsagesTestGenerator getGenerator(@NotNull TestEngine engine) {
    return new FindUsagesTestGenerator(engine.textsByFile, engine.markersByFile, new IdeaOffsetPositionConverter(getProject()));
  }

  @Override
  protected @NotNull Optional<?> getActual(@NotNull Object params) {
    final ReferenceParams refParams = (ReferenceParams) params;
    final var path = LspPath.fromLspUri(refParams.getTextDocument().getUri());
    final var future = new FindUsagesCommand(refParams.getPosition()).runAsync(getProject(), path);
    return Optional.ofNullable(TestUtil.getNonBlockingEdt(future, 50000));
  }
}
