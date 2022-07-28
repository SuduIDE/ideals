package org.rri.server.references;

import org.eclipse.lsp4j.Position;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.server.LspPath;

import java.util.HashSet;
import java.util.List;

@RunWith(JUnit4.class)
public class FindUsagesCommandJavaTest extends ReferencesCommandTestBase {
  @Before
  public void copyDirectoryToProject() {
    projectFile = myFixture.copyDirectoryToProject("java/project1/src", "");
  }

  @Test
  public void testFindUsagesJavaVariable() {
    final var file = projectFile.findChild("FindUsagesJavaVariable.java");
    assertNotNull(file);
    final var path = LspPath.fromVirtualFile(file);
    final var findUsagesJavaVariableUri = path.toLspUri();

    final var answers = new HashSet<>(List.of(
            location(findUsagesJavaVariableUri, range(3, 8, 3, 9)),
            location(findUsagesJavaVariableUri, range(7, 8, 7, 14)),
            location(findUsagesJavaVariableUri, range(11, 15, 11, 16))));

    final var positions = List.of(new Position(1, 16), new Position(3, 8));
    for (final var pos : positions) {
      check(answers, pos, path);
    }
  }

  @Test
  public void testFindUsagesJavaMethod() {
    var virtualFile = projectFile.findChild("org");
    assertNotNull(virtualFile);
    final var anotherFile = virtualFile.findChild("Another.java");
    assertNotNull(anotherFile);
    final var definitionFile = virtualFile.findChild("DefinitionJava.java");
    assertNotNull(definitionFile);
    final var anotherPath = LspPath.fromVirtualFile(anotherFile);
    final var definitionPath = LspPath.fromVirtualFile(definitionFile);

    final var anotherUri = anotherPath.toLspUri();
    final var definitionUri = definitionPath.toLspUri();

    final var answers = new HashSet<>(List.of(
            location(anotherUri, range(11, 8, 11, 26)),
            location(definitionUri, range(11, 8, 11, 11))));

    check(answers, new Position(11, 23), anotherPath);
    check(answers, new Position(5, 23), definitionPath);
  }

  @Test
  public void testFindUsagesJavaClass() {
    var virtualFile = projectFile.findChild("org");
    assertNotNull(virtualFile);
    final var anotherFile = virtualFile.findChild("Another.java");
    assertNotNull(anotherFile);
    final var definitionFile = virtualFile.findChild("DefinitionJava.java");
    assertNotNull(definitionFile);
    final var anotherPath = LspPath.fromVirtualFile(anotherFile);
    final var definitionPath = LspPath.fromVirtualFile(definitionFile);

    final var definitionJavaUri = PREFIX_FILE + "org/DefinitionJava.java";
    final var typeDefinitionJavaUri = PREFIX_FILE + "TypeDefinitionJava.java";

    final var answers = new HashSet<>(List.of(
            location(definitionJavaUri, range(13, 8, 13, 19)),
            location(definitionJavaUri, range(13, 28, 13, 39)),
            location(typeDefinitionJavaUri, range(0, 7, 0, 18)),
            location(typeDefinitionJavaUri, range(4, 8, 4, 15)),
            location(typeDefinitionJavaUri, range(5, 8, 5, 19))));

    check(answers, new Position(13, 12), definitionPath);
    check(answers, new Position(2, 13), anotherPath);
  }
}
