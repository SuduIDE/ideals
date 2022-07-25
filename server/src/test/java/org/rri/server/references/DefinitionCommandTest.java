package org.rri.server.references;

import com.intellij.psi.PsiPackage;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.server.LspContext;
import org.rri.server.LspPath;
import org.rri.server.TestUtil;
import org.rri.server.mocks.MockLanguageClient;
import org.rri.server.util.MiscUtil;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@RunWith(JUnit4.class)
public class DefinitionCommandTest extends BasePlatformTestCase {

  @Before
  public void setupContext() {
    LspContext.createContext(getProject(),
            new MockLanguageClient(),
            new ClientCapabilities()
    );
  }

  @Override
  protected String getTestDataPath() {
    return Paths.get("test-data/definitions").toAbsolutePath().toString();
  }

  @Test
  public void testDefinitionJava() {
    var virtualFile = myFixture.copyDirectoryToProject("java/project1", "");
    virtualFile = virtualFile.findChild("org");
    assertNotNull(virtualFile);
    virtualFile = virtualFile.findChild("DefinitionJava.java");
    assertNotNull(virtualFile);
    final var path = LspPath.fromVirtualFile(virtualFile);

    final var answers = answersJavaDefinition();
    final var file = MiscUtil.resolvePsiFile(getProject(), path);
    assertNotNull(file);
    final var doc = MiscUtil.getDocument(file);
    assertNotNull(doc);
    final int FILE_SIZE = 294;
    for (int offset = 0; offset < FILE_SIZE; ++offset) {
      final var future = new FindDefinitionCommand(MiscUtil.offsetToPosition(doc, offset)).runAsync(getProject(), path);
      final var lst = TestUtil.getNonBlockingEdt(future, 50000);
      assertNotNull(lst);
      final var result = lst.getRight();
      assertTrue(result.size() == 0 || result.size() == 1);

      final var elem = file.findElementAt(offset);
      assertNotNull(elem);
      if (elem instanceof PsiPackage) { continue; }
      var start = elem.getTextRange().getStartOffset();
      final var ans = answers.get(start);

      if (result.size() == 0) {
        assertNull(ans);
      } else {
        assertEquals(ans, result.get(0));
      }
    }
  }

  private Map<Integer, LocationLink> answersJavaDefinition() {
    Map<Integer, LocationLink> answers = new HashMap<>(); // <offset, location>
    final var privateStaticIntX = range(3, 23, 3, 24);
    final var publicStaticVoidFoo = range(5, 23, 5, 26);
    final var intZ = range(8, 12, 8, 13);
    final var classOrgAnother = range(2, 6, 2, 13);
    final var constructorOrgAnother = range(9, 11, 9, 18);
    final var classComAnother = range(2, 13, 2, 20);
    final var constructorComAnother = range(6, 11, 6, 18);
    final var prefix = "temp:///src/";
    final var definitionJavaUri = prefix + "org/DefinitionJava.java";
    final var orgAnotherUri = prefix + "org/Another.java";
    final var comAnotherUri = prefix + "com/Another.java";
    answers.put(164, locationLink(definitionJavaUri, intZ, range(9, 16, 9, 17)));
    answers.put(165, locationLink(definitionJavaUri, intZ, range(9, 17, 9, 18)));
    answers.put(168, locationLink(definitionJavaUri, privateStaticIntX, range(9, 20, 9, 21)));
    answers.put(169, locationLink(definitionJavaUri, privateStaticIntX, range(9, 21, 9, 22)));
    answers.put(180, locationLink(definitionJavaUri, publicStaticVoidFoo, range(11, 8, 11, 11)));
    answers.put(183, locationLink(definitionJavaUri, publicStaticVoidFoo, range(11, 11, 11, 12)));
    answers.put(200, locationLink(orgAnotherUri, classOrgAnother, range(13, 12, 13, 19)));
    answers.put(207, locationLink(orgAnotherUri, classOrgAnother, range(13, 19, 13, 20)));
    answers.put(220, locationLink(orgAnotherUri, constructorOrgAnother, range(13, 32, 13, 39)));
    answers.put(227, locationLink(orgAnotherUri, constructorOrgAnother, range(13, 39, 13, 40)));
    answers.put(249, locationLink(comAnotherUri, classComAnother, range(14, 12, 14, 19)));
    answers.put(256, locationLink(comAnotherUri, classComAnother, range(14, 19, 14, 20)));
    answers.put(270, locationLink(comAnotherUri, constructorComAnother, range(14, 33, 14, 40)));
    answers.put(277, locationLink(comAnotherUri, constructorComAnother, range(14, 40, 14, 41)));
    return answers;
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
