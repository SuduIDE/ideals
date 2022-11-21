package org.rri.ideals.server.references;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.TestUtil;

import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;


@RunWith(JUnit4.class)
public class DefinitionInJDKSourcesTest extends LightJavaCodeInsightFixtureTestCase {
  static protected LightProjectDescriptor pd = new ProjectDescriptor(LanguageLevel.JDK_11).withRepositoryLibrary("com.squareup:javapoet:1.13.0");

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return pd;
  }

  @Override
  protected String getTestDataPath() {
    return Paths.get("test-data/references").toAbsolutePath().toString();
  }

  @Test
  @Ignore // TODO answer from server always empty
  public void definitionJavaTest() {
    myFixture.copyDirectoryToProject("java/project-definition-bug/", "");
    final var path =
        LspPath.fromLocalPath(
            Paths.get(getTestDataPath()).resolve("java/project-definition-bug/src/DefinitionFromJar.java"));
    final var future = new FindTypeDefinitionCommand(new Position(3, 8)).runAsync(getProject(), path);
    var actual = TestUtil.getNonBlockingEdt(future, 50000);
    assertNotNull(actual);
    assertEquals(Set.of(), new HashSet<>(actual.getRight()));
  }
}
