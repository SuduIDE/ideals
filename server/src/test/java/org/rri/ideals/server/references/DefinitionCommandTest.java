//package org.rri.ideals.server.references;
//
//import org.eclipse.lsp4j.DefinitionParams;
//import org.eclipse.lsp4j.LocationLink;
//import org.eclipse.lsp4j.jsonrpc.messages.Either;
//import org.jetbrains.annotations.NotNull;
//import org.jetbrains.annotations.Nullable;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.junit.runners.JUnit4;
//import org.rri.ideals.server.LspPath;
//import org.rri.ideals.server.TestUtil;
//import org.rri.ideals.server.engine.TestEngine;
//import org.rri.ideals.server.generator.IdeaOffsetPositionConverter;
//import org.rri.ideals.server.references.generators.DefinitionTestGenerator;
//
//import java.nio.file.Paths;
//import java.util.List;
//import java.util.Optional;
//
//
//@RunWith(JUnit4.class)
//public class DefinitionCommandTest extends ReferencesCommandTestBase<DefinitionTestGenerator> {
//  @Test
//  public void definitionJavaTest() {
//    final var dirPath = Paths.get(getTestDataPath(), "java/project-definition");
//    checkReferencesByDirectory(dirPath);
//  }
//
//  @Test
//  public void definitionPythonTest() {
//    final var dirPath = Paths.get(getTestDataPath(), "python/project-definition");
//    checkReferencesByDirectory(dirPath);
//  }
//
//
//  @Override
//  protected @NotNull DefinitionTestGenerator getGenerator(@NotNull TestEngine engine) {
//    return new DefinitionTestGenerator(engine.getTextsByFile(), engine.getMarkersByFile(), new IdeaOffsetPositionConverter(getProject()));
//  }
//
//  @Override
//  @Nullable
//  protected List<? extends LocationLink> getActuals(@NotNull Object params) {
//    final DefinitionParams defParams = (DefinitionParams) params;
//    final var path = LspPath.fromLspUri(defParams.getTextDocument().getUri());
//    final var future = new FindDefinitionCommand(defParams.getPosition()).runAsync(getProject(), path);
//    return Optional.ofNullable(TestUtil.getNonBlockingEdt(future, 50000)).map(Either::getRight).orElse(null);
//  }
//}
