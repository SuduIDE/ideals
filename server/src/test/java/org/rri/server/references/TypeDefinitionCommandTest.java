package org.rri.server.references;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.server.LspPath;
import org.rri.server.TestUtil;
import org.rri.server.util.MiscUtil;

import java.nio.file.Paths;
import java.util.List;

@RunWith(JUnit4.class)
public class TypeDefinitionCommandTest extends BasePlatformTestCase {

  @Override
  protected String getTestDataPath() {
    return Paths.get("test-data/references").toAbsolutePath().toString();
  }

  @Test
  public void testTypeDefinitionJava() {
    var virtualFile = myFixture.copyDirectoryToProject("java/project1/src", "");
    virtualFile = virtualFile.findChild("TypeDefinitionJava.java");
    assertNotNull(virtualFile);
    final var path = LspPath.fromVirtualFile(virtualFile);

    final var prefix = "temp:///src/";
    final var orgAnotherUri = prefix + "org/Another.java";
    final var comAnotherUri = prefix + "com/Another.java";
    final var classOrgAnother = range(2, 13, 2, 20);
    final var classComAnother = range(2, 13, 2, 20);

    final var file = MiscUtil.resolvePsiFile(getProject(), path);
    assertNotNull(file);
    final var doc = MiscUtil.getDocument(file);
    assertNotNull(doc);

    final var positions = List.of(new Position(4, 16), new Position(5, 20), new Position(6, 20));
    final var answers = List.of(locationLink(orgAnotherUri, classOrgAnother, range(4, 16, 4, 17)),
            locationLink(orgAnotherUri, classOrgAnother, range(5, 20, 5, 22)),
            locationLink(comAnotherUri, classComAnother, range(6, 20, 6, 21)));
    for (int ind = 0; ind < 3; ++ind) {
      final var pos = positions.get(ind);
      final var ans = answers.get(ind);
      final var elem = file.findElementAt(MiscUtil.positionToOffset(pos, doc));
      assertNotNull(elem);

      final var future = new FindTypeDefinitionCommand(pos).runAsync(getProject(), path);
      final var lst = TestUtil.getNonBlockingEdt(future, 50000);
      assertNotNull(lst);
      final var result = lst.getRight();
      assertEquals(1, result.size());
      assertEquals(ans, result.get(0));
    }
  }

  @Test
  public void testTypeDefinitionPython() {
    var virtualFile = myFixture.copyDirectoryToProject("python/projectDefinition", "");
    virtualFile = virtualFile.findChild("typeDefinitionPython.py");
    assertNotNull(virtualFile);
    final var path = LspPath.fromVirtualFile(virtualFile);

    final var prefix = "temp:///src/";
    final var secondUri = prefix + "second.py";
    final var class1Uri = prefix + "class1.py";
    final var class2Uri = prefix + "class2.py";
    final var A = range(0, 6, 0, 7);
    final var class1B = range(0, 6, 0, 7);
    final var class2B = range(0, 6, 0, 7);

    final var file = MiscUtil.resolvePsiFile(getProject(), path);
    assertNotNull(file);
    final var doc = MiscUtil.getDocument(file);
    assertNotNull(doc);

    final var positions = List.of(new Position(5, 0), new Position(6, 0), new Position(7, 0));
    final var answers = List.of(locationLink(secondUri, A, range(5, 0, 5, 1)),
            locationLink(class1Uri, class1B, range(6, 0, 6, 1)),
            locationLink(class2Uri, class2B, range(7, 0, 7, 2)));
    for (int ind = 0; ind < 3; ++ind) {
      final var pos = positions.get(ind);
      final var ans = answers.get(ind);
      final var elem = file.findElementAt(MiscUtil.positionToOffset(pos, doc));
      assertNotNull(elem);

      final var future = new FindTypeDefinitionCommand(pos).runAsync(getProject(), path);
      final var lst = TestUtil.getNonBlockingEdt(future, 50000);
      assertNotNull(lst);
      final var result = lst.getRight();
      assertEquals(1, result.size());
      assertEquals(ans, result.get(0));
    }
  }

  private LocationLink locationLink(String uri, Range targetRange, Range originalRange) {
    return new LocationLink(uri, targetRange, targetRange, originalRange);
  }

  @SuppressWarnings("SameParameterValue")
  @NotNull
  private static Range range(int line1, int char1, int line2, int char2) {
    return new Range(new Position(line1, char1), new Position(line2, char2));
  }
}
