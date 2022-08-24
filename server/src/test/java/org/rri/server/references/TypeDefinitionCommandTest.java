package org.rri.server.references;

import com.intellij.openapi.vfs.VirtualFile;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.server.LspPath;
import org.rri.server.TestUtil;
import org.rri.server.util.MiscUtil;

import java.util.Map;

import static org.rri.server.TestUtil.newRange;

@RunWith(JUnit4.class)
public class TypeDefinitionCommandTest extends ReferencesCommandTestBase {
  @Test
  public void testTypeDefinitionJava() {
    final var virtualFile = myFixture.copyDirectoryToProject("java/project1/src", "");

    final var orgAnotherUri = PREFIX_FILE + "org/Another.java";
    final var comAnotherUri = PREFIX_FILE + "com/Another.java";
    final var classOrgAnother = newRange(2, 13, 2, 20);
    final var classComAnother = newRange(2, 13, 2, 20);

    final var checks = Map.of(new Position(4, 16), locationLink(orgAnotherUri, classOrgAnother, newRange(4, 16, 4, 17)),
            new Position(5, 20), locationLink(orgAnotherUri, classOrgAnother, newRange(5, 20, 5, 22)),
            new Position(6, 20), locationLink(comAnotherUri, classComAnother, newRange(6, 20, 6, 21)));

    checkTypeDefinitions(virtualFile.findChild("TypeDefinitionJava.java"), checks);
  }

  @Test
  public void testTypeDefinitionPython() {
    final var virtualFile = myFixture.copyDirectoryToProject("python/project1", "");

    final var secondUri = PREFIX_FILE + "second.py";
    final var class1Uri = PREFIX_FILE + "class1.py";
    final var class2Uri = PREFIX_FILE + "class2.py";
    final var A = newRange(0, 6, 0, 7);
    final var class1B = newRange(0, 6, 0, 7);
    final var class2B = newRange(0, 6, 0, 7);

    final var checks = Map.of(new Position(5, 0), locationLink(secondUri, A, newRange(5, 0, 5, 1)),
            new Position(6, 0), locationLink(class1Uri, class1B, newRange(6, 0, 6, 1)),
            new Position(7, 0), locationLink(class2Uri, class2B, newRange(7, 0, 7, 2)));

    checkTypeDefinitions(virtualFile.findChild("typeDefinitionPython.py"), checks);
  }

  private void checkTypeDefinitions(@Nullable VirtualFile virtualFile, @NotNull Map<@NotNull Position, @NotNull LocationLink> checks) {
    assertNotNull(virtualFile);
    final var path = LspPath.fromVirtualFile(virtualFile);
    final var file = MiscUtil.resolvePsiFile(getProject(), path);
    assertNotNull(file);
    final var doc = MiscUtil.getDocument(file);
    assertNotNull(doc);

    for (final var entry : checks.entrySet()) {
      final var pos = entry.getKey();
      final var ans = entry.getValue();
      final var elem = file.findElementAt(MiscUtil.positionToOffset(doc, pos));
      assertNotNull(elem);

      final var future = new FindTypeDefinitionCommand(pos).runAsync(getProject(), path);
      final var lst = TestUtil.getNonBlockingEdt(future, 50000);
      assertNotNull(lst);
      final var result = lst.getRight();
      assertEquals(1, result.size());
      assertEquals(ans, result.get(0));
    }
  }
}
