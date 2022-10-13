package org.rri.ideals.server.references;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.TestUtil;
import org.rri.ideals.server.engine.IdeaTestFixture;
import org.rri.ideals.server.engine.TestEngine;
import org.rri.ideals.server.generator.IdeaOffsetPositionConverter;
import org.rri.ideals.server.generator.TestGenerator;
import org.rri.ideals.server.references.engines.DefinitionTestGenerator;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public abstract class ReferencesCommandTestBase<E extends TestGenerator<? extends TestGenerator.Test>> extends BasePlatformTestCase {
  @Override
  protected String getTestDataPath() {
    return Paths.get("test-data/references").toAbsolutePath().toString();
  }

  protected abstract @NotNull E getGenerator(@NotNull final TestEngine engine);

  protected abstract @NotNull Optional<?> getActual(@NotNull final Object params);

  protected void checkReferencesByDirectory(@NotNull Path dirPath) {
    try {
      final var engine = new TestEngine(dirPath);
      engine.initSandbox(new IdeaTestFixture(myFixture));
      final var generator = getGenerator(engine);
      final var referencesTests = generator.generateTests();
      for (final var test : referencesTests) {
        final var params = test.params();
        final var answer = test.answer();

        final var actual = getActual(params);

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
