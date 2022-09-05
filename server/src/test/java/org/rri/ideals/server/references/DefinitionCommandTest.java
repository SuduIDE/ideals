package org.rri.ideals.server.references;

import org.eclipse.lsp4j.LocationLink;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.TestUtil;
import org.rri.ideals.server.util.MiscUtil;

import java.util.HashMap;
import java.util.Map;

@RunWith(JUnit4.class)
public class DefinitionCommandTest extends ReferencesCommandTestBase {
  @Test
  public void testDefinitionJava() {
    var virtualFile = myFixture.copyDirectoryToProject("java/project1/src", "");
    virtualFile = virtualFile.findChild("org");
    assertNotNull(virtualFile);
    virtualFile = virtualFile.findChild("DefinitionJava.java");
    assertNotNull(virtualFile);
    final var path = LspPath.fromVirtualFile(virtualFile);
    checkDefinitions(path, answersJavaDefinition(), 294);
  }

  @Test
  public void testDefinitionPython() {
    var virtualFile = myFixture.copyDirectoryToProject("python/project1", "");
    virtualFile = virtualFile.findChild("definitionPython.py");
    assertNotNull(virtualFile);
    final var path = LspPath.fromVirtualFile(virtualFile);
    checkDefinitions(path, answersPythonDefinition(), 127);
  }

  private void checkDefinitions(LspPath path, Map<Integer, LocationLink> answers, int FILE_SIZE) {
    final var file = MiscUtil.resolvePsiFile(getProject(), path);
    assertNotNull(file);
    final var doc = MiscUtil.getDocument(file);
    assertNotNull(doc);
    for (int offset = 0; offset < FILE_SIZE; ++offset) {
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
    final var privateStaticIntX = TestUtil.newRange(3, 23, 3, 24);
    final var publicStaticVoidFoo = TestUtil.newRange(5, 23, 5, 26);
    final var intZ = TestUtil.newRange(8, 12, 8, 13);
    final var classOrgAnother = TestUtil.newRange(2, 13, 2, 20);
    final var constructorOrgAnother = TestUtil.newRange(9, 11, 9, 18);
    final var classComAnother = TestUtil.newRange(2, 13, 2, 20);
    final var constructorComAnother = TestUtil.newRange(6, 11, 6, 18);
    final var definitionJavaUri = PREFIX_FILE + "org/DefinitionJava.java";
    final var orgAnotherUri = PREFIX_FILE + "org/Another.java";
    final var comAnotherUri = PREFIX_FILE + "com/Another.java";
    answers.put(164, locationLink(definitionJavaUri, intZ, TestUtil.newRange(9, 16, 9, 17)));
    answers.put(165, locationLink(definitionJavaUri, intZ, TestUtil.newRange(9, 17, 9, 18)));
    answers.put(168, locationLink(definitionJavaUri, privateStaticIntX, TestUtil.newRange(9, 20, 9, 21)));
    answers.put(169, locationLink(definitionJavaUri, privateStaticIntX, TestUtil.newRange(9, 21, 9, 22)));
    answers.put(180, locationLink(definitionJavaUri, publicStaticVoidFoo, TestUtil.newRange(11, 8, 11, 11)));
    answers.put(183, locationLink(definitionJavaUri, publicStaticVoidFoo, TestUtil.newRange(11, 11, 11, 12)));
    answers.put(200, locationLink(orgAnotherUri, classOrgAnother, TestUtil.newRange(13, 12, 13, 19)));
    answers.put(207, locationLink(orgAnotherUri, classOrgAnother, TestUtil.newRange(13, 19, 13, 20)));
    answers.put(220, locationLink(orgAnotherUri, constructorOrgAnother, TestUtil.newRange(13, 32, 13, 39)));
    answers.put(227, locationLink(orgAnotherUri, constructorOrgAnother, TestUtil.newRange(13, 39, 13, 40)));
    answers.put(249, locationLink(comAnotherUri, classComAnother, TestUtil.newRange(14, 12, 14, 19)));
    answers.put(256, locationLink(comAnotherUri, classComAnother, TestUtil.newRange(14, 19, 14, 20)));
    answers.put(270, locationLink(comAnotherUri, constructorComAnother, TestUtil.newRange(14, 33, 14, 40)));
    answers.put(277, locationLink(comAnotherUri, constructorComAnother, TestUtil.newRange(14, 40, 14, 41)));
    return answers;
  }

  private Map<Integer, LocationLink> answersPythonDefinition() {
    Map<Integer, LocationLink> answers = new HashMap<>();
    final var func = TestUtil.newRange(5, 4, 5, 8);
    final var a = TestUtil.newRange(8, 0, 8, 1);
    final var A = TestUtil.newRange(0, 6, 0, 7);
    final var aa = TestUtil.newRange(9, 0, 9, 2);
    final var b1 = TestUtil.newRange(12, 0, 12, 2);
    final var b2 = TestUtil.newRange(13, 0, 13, 2);
    final var second = TestUtil.newRange(0, 0, 1, 8);
    final var class1 = TestUtil.newRange(0, 0, 8, 8);
    final var class2 = TestUtil.newRange(0, 0, 1, 8);
    final var class1B = TestUtil.newRange(0, 6, 0, 7);
    final var class2B = TestUtil.newRange(0, 6, 0, 7);
    final var definitionPythonUrl = PREFIX_FILE + "definitionPython.py";
    final var secondUrl = PREFIX_FILE + "second.py";
    final var class1Url = PREFIX_FILE + "class1.py";
    final var class2Url = PREFIX_FILE + "class2.py";
    answers.put(7, locationLink(class1Url, class1, TestUtil.newRange(0, 7, 0, 13)));
    answers.put(13, locationLink(class1Url, class1, TestUtil.newRange(0, 13, 1, 0)));
    answers.put(21, locationLink(class2Url, class2, TestUtil.newRange(1, 7, 1, 13)));
    answers.put(27, locationLink(class2Url, class2, TestUtil.newRange(1, 13, 2, 0)));
    answers.put(33, locationLink(secondUrl, second, TestUtil.newRange(2, 5, 2, 11)));
    answers.put(39, locationLink(secondUrl, second, TestUtil.newRange(2, 11, 2, 12)));
    answers.put(73, locationLink(definitionPythonUrl, a, a));
    answers.put(74, locationLink(definitionPythonUrl, a, TestUtil.newRange(8, 1, 8, 2)));
    answers.put(77, locationLink(secondUrl, A, TestUtil.newRange(8, 4, 8, 5)));
    answers.put(78, locationLink(secondUrl, A, TestUtil.newRange(8, 5, 8, 6)));
    answers.put(81, locationLink(definitionPythonUrl, aa, aa));
    answers.put(83, locationLink(definitionPythonUrl, aa, TestUtil.newRange(9, 2, 9, 3)));
    answers.put(86, locationLink(definitionPythonUrl, a, TestUtil.newRange(9, 5, 9, 6)));
    answers.put(87, locationLink(definitionPythonUrl, a, TestUtil.newRange(9, 6, 10, 0)));
    answers.put(88, locationLink(definitionPythonUrl, func, TestUtil.newRange(10, 0, 10, 4)));
    answers.put(92, locationLink(definitionPythonUrl, func, TestUtil.newRange(10, 4, 10, 5)));
    answers.put(96, locationLink(definitionPythonUrl, b1, b1));
    answers.put(98, locationLink(definitionPythonUrl, b1, TestUtil.newRange(12, 2, 12, 3)));
    answers.put(101, locationLink(class1Url, class1, TestUtil.newRange(12, 5, 12, 11)));
    answers.put(107, locationLink(class1Url, class1, TestUtil.newRange(12, 11, 12, 12)));
    answers.put(108, locationLink(class1Url, class1B, TestUtil.newRange(12, 12, 12, 13)));
    answers.put(109, locationLink(class1Url, class1B, TestUtil.newRange(12, 13, 12, 14)));
    answers.put(112, locationLink(definitionPythonUrl, b2, b2));
    answers.put(114, locationLink(definitionPythonUrl, b2, TestUtil.newRange(13, 2, 13, 3)));
    answers.put(117, locationLink(class2Url, class2, TestUtil.newRange(13, 5, 13, 11)));
    answers.put(123, locationLink(class2Url, class2, TestUtil.newRange(13, 11, 13, 12)));
    answers.put(124, locationLink(class2Url, class2B, TestUtil.newRange(13, 12, 13, 13)));
    answers.put(125, locationLink(class2Url, class2B, TestUtil.newRange(13, 13, 13, 14)));
    return answers;
  }
}
