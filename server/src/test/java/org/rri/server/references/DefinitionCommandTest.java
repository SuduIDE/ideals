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
    return Paths.get("test-data/references").toAbsolutePath().toString();
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
      final var elem = file.findElementAt(offset);
      assertNotNull(elem);
      if (elem instanceof PsiPackage || file.findElementAt(offset - 1) instanceof PsiPackage) { continue; }
      var start = elem.getTextRange().getStartOffset();
      final var ans = answers.get(start);

      final var future = new FindDefinitionCommand(MiscUtil.offsetToPosition(doc, offset)).runAsync(getProject(), path);
      final var lst = TestUtil.getNonBlockingEdt(future, 50000);
      assertNotNull(lst);
      final var result = lst.getRight();
      assertTrue(result.size() == 0 || result.size() == 1);

      if (result.size() == 0) {
        assertNull(ans);
      } else {
        assertEquals(ans, result.get(0));
      }
    }
  }

  @Test
  public void testDefinitionPython() {
    var virtualFile = myFixture.copyDirectoryToProject("python/projectDefinition", "");
    virtualFile = virtualFile.findChild("first.py");
    assertNotNull(virtualFile);
    final var path = LspPath.fromVirtualFile(virtualFile);

    final var answers = answersPythonDefinition();
    final var file = MiscUtil.resolvePsiFile(getProject(), path);
    assertNotNull(file);
    final var doc = MiscUtil.getDocument(file);
    assertNotNull(doc);
    final int FILE_SIZE = 126;
    for (int offset = 0; offset < FILE_SIZE; ++offset) {
      if (offset == 49) { continue; }
      final var elem = file.findElementAt(offset);
      assertNotNull(elem);
      var start = elem.getTextRange().getStartOffset();
      final var ans = answers.get(start);

      final var future = new FindDefinitionCommand(MiscUtil.offsetToPosition(doc, offset)).runAsync(getProject(), path);
      final var lst = TestUtil.getNonBlockingEdt(future, 50000);
      assertNotNull(lst);
      final var result = lst.getRight();
      assertTrue(result.size() == 0 || result.size() == 1);

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

  private Map<Integer, LocationLink> answersPythonDefinition() {
    Map<Integer, LocationLink> answers = new HashMap<>();
    final var func = range(4, 4, 4, 8);
    final var a = range(7, 0, 7, 1);
    final var A = range(0, 6, 0, 7);
    final var aa = range(8, 0, 8, 2);
    final var b1 = range(11, 0, 11, 2);
    final var b2 = range(12, 0, 12, 2);
    final var second = range(0, 0, 1, 8);
    final var class1 = range(0, 0, 7, 8);
    final var class2 = range(0, 0, 1, 8);
    final var class1B = range(0, 6, 0, 7);
    final var class2B = range(0, 6, 0, 7);
    final var prefix = "temp:///src/";
    final var firstUrl = prefix + "first.py";
    final var secondUrl = prefix + "second.py";
    final var class1Url = prefix + "class1.py";
    final var class2Url = prefix + "class2.py";
    answers.put(5, locationLink(secondUrl, second, range(0, 5, 0, 11)));
    answers.put(11, locationLink(secondUrl, second, range(0, 11, 0, 12)));
    answers.put(28, locationLink(class1Url, class1, range(1, 7, 1, 13)));
    answers.put(34, locationLink(class1Url, class1, range(1, 13, 2, 0)));
    answers.put(42, locationLink(class2Url, class2, range(2, 7, 2, 13)));
    answers.put(48, locationLink(class2Url, class2, range(2, 13, 4, 0)));
    answers.put(72, locationLink(firstUrl, a, a));
    answers.put(73, locationLink(firstUrl, a, range(7, 1, 7, 2)));
    answers.put(76, locationLink(secondUrl, A, range(7, 4, 7, 5)));
    answers.put(77, locationLink(secondUrl, A, range(7, 5, 7, 6)));
    answers.put(80, locationLink(firstUrl, aa, aa));
    answers.put(82, locationLink(firstUrl, aa, range(8, 2, 8, 3)));
    answers.put(85, locationLink(firstUrl, a, range(8, 5, 8, 6)));
    answers.put(86, locationLink(firstUrl, a, range(8, 6, 9, 0)));
    answers.put(87, locationLink(firstUrl, func, range(9, 0, 9, 4)));
    answers.put(91, locationLink(firstUrl, func, range(9, 4, 9, 5)));
    answers.put(95, locationLink(firstUrl, b1, b1));
    answers.put(97, locationLink(firstUrl, b1, range(11, 2, 11, 3)));
    answers.put(100, locationLink(class1Url, class1, range(11, 5, 11, 11)));
    answers.put(106, locationLink(class1Url, class1, range(11, 11, 11, 12)));
    answers.put(107, locationLink(class1Url, class1B, range(11, 12, 11, 13)));
    answers.put(108, locationLink(class1Url, class1B, range(11, 13, 11, 14)));
    answers.put(111, locationLink(firstUrl, b2, b2));
    answers.put(113, locationLink(firstUrl, b2, range(12, 2, 12, 3)));
    answers.put(116, locationLink(class2Url, class2, range(12, 5, 12, 11)));
    answers.put(122, locationLink(class2Url, class2, range(12, 11, 12, 12)));
    answers.put(123, locationLink(class2Url, class2B, range(12, 12, 12, 13)));
    answers.put(124, locationLink(class2Url, class2B, range(12, 13, 12, 14)));
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
