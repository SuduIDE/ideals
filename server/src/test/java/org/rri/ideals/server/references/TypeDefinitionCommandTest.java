package org.rri.ideals.server.references;

import org.eclipse.lsp4j.TypeDefinitionParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.TestUtil;
import org.rri.ideals.server.engine.TestEngine;
import org.rri.ideals.server.generator.IdeaOffsetPositionConverter;
import org.rri.ideals.server.references.engines.TypeDefinitionTestGenerator;

import java.nio.file.Paths;
import java.util.Optional;

@RunWith(JUnit4.class)
public class TypeDefinitionCommandTest extends ReferencesCommandTestBase<TypeDefinitionTestGenerator> {
  @Test
  public void testTypeDefinitionJava() {
    final var dirPath = Paths.get(getTestDataPath(), "java/project-type-definition");
    checkReferencesByDirectory(dirPath);
  }

  @Test
  public void testTypeDefinitionPython() {
    final var dirPath = Paths.get(getTestDataPath(), "python/project-type-definition");
    checkReferencesByDirectory(dirPath);
  }

  @Override
  protected @NotNull TypeDefinitionTestGenerator getGenerator(@NotNull TestEngine engine) {
    return new TypeDefinitionTestGenerator(engine.textsByFile, engine.markersByFile, new IdeaOffsetPositionConverter(getProject()));
  }

  @Override
  protected @NotNull Optional<?> getActual(@NotNull Object params) {
    final TypeDefinitionParams typeDefParams = (TypeDefinitionParams) params;
    final var path = LspPath.fromLspUri(typeDefParams.getTextDocument().getUri());
    final var future = new FindTypeDefinitionCommand(typeDefParams.getPosition()).runAsync(getProject(), path);
    return Optional.ofNullable(TestUtil.getNonBlockingEdt(future, 50000)).map(Either::getRight);
  }
}
