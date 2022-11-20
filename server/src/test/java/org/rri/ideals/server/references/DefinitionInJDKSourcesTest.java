package org.rri.ideals.server.references;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;
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

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
  }

  @Override
  protected String getTestDataPath() {
    return Paths.get("test-data/references").toAbsolutePath().toString();
  }

  @Test
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
