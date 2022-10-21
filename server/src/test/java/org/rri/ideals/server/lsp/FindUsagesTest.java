//package org.rri.ideals.server.lsp;
//
//import org.jetbrains.annotations.NotNull;
//import org.junit.Test;
//import org.rri.ideals.server.TestUtil;
//import org.rri.ideals.server.generator.IdeaOffsetPositionConverter;
//import org.rri.ideals.server.references.generators.FindUsagesTestGenerator;
//
//import java.nio.file.Path;
//import java.util.HashSet;
//import java.util.Optional;
//
//public class FindUsagesTest extends LspServerTestWithEngineBase {
//  @Override
//  protected String getProjectRelativePath() {
//    return "sandbox";
//  }
//
//  @Override
//  protected @NotNull Path getTargetProjectPath() {
//    return getTestDataRoot().resolve("references/java/project-find-usages-integration");
//  }
//
//  @Test
//  public void findUsages() {
//    try {
//      final var generator = new FindUsagesTestGenerator(this.getEngine().getTextsByFile(), this.getEngine().getMarkersByFile(), new IdeaOffsetPositionConverter(server().getProject()));
//      final var definitionTests = generator.generateTests();
//      for (final var test : definitionTests) {
//        final var params = test.params();
//        final var answer = test.expected();
//
//        final var future = server().getTextDocumentService().references(params);
//        final var actual = Optional.ofNullable(TestUtil.getNonBlockingEdt(future, 50000));
//
//        assertTrue(actual.isPresent());
//        assertEquals(answer, new HashSet<>(actual.get()));
//      }
//    } catch (RuntimeException e) {
//      e.printStackTrace();
//      fail();
//    }
//  }
//}
