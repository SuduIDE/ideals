package org.rri.ideals.server.references;

import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.TestEngine;
import org.rri.ideals.server.TestUtil;
import org.rri.ideals.server.util.MiscUtil;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.rri.ideals.server.TestUtil.newRange;

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

  @Test
  public void polivariantDefinitionJava() {
    var virtualFile = myFixture.copyDirectoryToProject("java/project1/src", "");
    virtualFile = virtualFile.findChild("PolivariantDefinition.java");
    assertNotNull(virtualFile);
    final var path = LspPath.fromVirtualFile(virtualFile);
    final var future = new FindDefinitionCommand(new Position(10, 5)).runAsync(getProject(), path);
    final var lst = TestUtil.getNonBlockingEdt(future, 50000);
    assertNotNull(lst);
    final var result = lst.getRight();

    final var foo1 = newRange(1, 14, 1, 17);
    final var foo2 = newRange(5, 14, 5, 17);
    final var fooOrigin = newRange(10, 4, 10, 7);
    final var answers = List.of(locationLink(path.toLspUri(), foo1, fooOrigin),
        locationLink(path.toLspUri(), foo2, fooOrigin));

    assertEquals(answers, result);
  }

  private Map<Integer, LocationLink> answersJavaDefinition() {
    Map<Integer, LocationLink> answers = new HashMap<>(); // <offset, location>
    final var classDefinitionJava = newRange(2, 6, 2, 20);
    final var privateStaticIntX = newRange(3, 23, 3, 24);
    final var publicStaticVoidFoo = newRange(5, 23, 5, 26);
    final var publicStaticVoidMain = newRange(7, 23, 7, 27);
    final var intZ = newRange(8, 12, 8, 13);
    final var intC = newRange(9, 12, 9, 13);
    final var classOrgAnother = newRange(2, 13, 2, 20);
    final var constructorOrgAnother = newRange(9, 11, 9, 18);
    final var anotherM = newRange(13, 20, 13, 21);
    final var classComAnother = newRange(2, 13, 2, 20);
    final var constructorComAnother = newRange(6, 11, 6, 18);
    final var anotherMM = newRange(14, 20, 14, 22);
    final var definitionJavaUri = PREFIX_FILE + "org/DefinitionJava.java";
    final var orgAnotherUri = PREFIX_FILE + "org/Another.java";
    final var comAnotherUri = PREFIX_FILE + "com/Another.java";
    answers.put(20, locationLink(definitionJavaUri, classDefinitionJava, classDefinitionJava));
    answers.put(34, locationLink(definitionJavaUri, classDefinitionJava, newRange(2, 20, 2, 21)));
    answers.put(60, locationLink(definitionJavaUri, privateStaticIntX, privateStaticIntX));
    answers.put(61, locationLink(definitionJavaUri, privateStaticIntX, newRange(3, 24, 3, 25)));
    answers.put(87, locationLink(definitionJavaUri, publicStaticVoidFoo, publicStaticVoidFoo));
    answers.put(90, locationLink(definitionJavaUri, publicStaticVoidFoo, newRange(5, 26, 5, 27)));
    answers.put(120, locationLink(definitionJavaUri, publicStaticVoidMain, publicStaticVoidMain));
    answers.put(124, locationLink(definitionJavaUri, publicStaticVoidMain, newRange(7, 27, 7, 28)));
    answers.put(141, locationLink(definitionJavaUri, intZ, intZ));
    answers.put(142, locationLink(definitionJavaUri, intZ, newRange(8, 13, 8, 14)));
    answers.put(160, locationLink(definitionJavaUri, intC, intC));
    answers.put(161, locationLink(definitionJavaUri, intC, newRange(9, 13, 9, 14)));
    answers.put(164, locationLink(definitionJavaUri, intZ, newRange(9, 16, 9, 17)));
    answers.put(165, locationLink(definitionJavaUri, intZ, newRange(9, 17, 9, 18)));
    answers.put(168, locationLink(definitionJavaUri, privateStaticIntX, newRange(9, 20, 9, 21)));
    answers.put(169, locationLink(definitionJavaUri, privateStaticIntX, newRange(9, 21, 9, 22)));
    answers.put(180, locationLink(definitionJavaUri, publicStaticVoidFoo, newRange(11, 8, 11, 11)));
    answers.put(183, locationLink(definitionJavaUri, publicStaticVoidFoo, newRange(11, 11, 11, 12)));
    answers.put(200, locationLink(orgAnotherUri, classOrgAnother, newRange(13, 12, 13, 19)));
    answers.put(207, locationLink(orgAnotherUri, classOrgAnother, newRange(13, 19, 13, 20)));
    answers.put(208, locationLink(definitionJavaUri, anotherM, anotherM));
    answers.put(209, locationLink(definitionJavaUri, anotherM, newRange(13, 21, 13, 22)));
    answers.put(220, locationLink(orgAnotherUri, constructorOrgAnother, newRange(13, 32, 13, 39)));
    answers.put(227, locationLink(orgAnotherUri, constructorOrgAnother, newRange(13, 39, 13, 40)));
    answers.put(249, locationLink(comAnotherUri, classComAnother, newRange(14, 12, 14, 19)));
    answers.put(256, locationLink(comAnotherUri, classComAnother, newRange(14, 19, 14, 20)));
    answers.put(257, locationLink(definitionJavaUri, anotherMM, anotherMM));
    answers.put(259, locationLink(definitionJavaUri, anotherMM, newRange(14, 22, 14, 23)));
    answers.put(270, locationLink(comAnotherUri, constructorComAnother, newRange(14, 33, 14, 40)));
    answers.put(277, locationLink(comAnotherUri, constructorComAnother, newRange(14, 40, 14, 41)));
    return answers;
  }

  private Map<Integer, LocationLink> answersPythonDefinition() {
    Map<Integer, LocationLink> answers = new HashMap<>();
    final var func = newRange(5, 4, 5, 8);
    final var a = newRange(8, 0, 8, 1);
    final var A = newRange(0, 6, 0, 7);
    final var aa = newRange(9, 0, 9, 2);
    final var b1 = newRange(12, 0, 12, 2);
    final var b2 = newRange(13, 0, 13, 2);
    final var second = newRange(0, 0, 1, 8);
    final var class1 = newRange(0, 0, 8, 8);
    final var class2 = newRange(0, 0, 1, 8);
    final var class1B = newRange(0, 6, 0, 7);
    final var class2B = newRange(0, 6, 0, 7);
    final var definitionPythonUrl = PREFIX_FILE + "definitionPython.py";
    final var secondUrl = PREFIX_FILE + "second.py";
    final var class1Url = PREFIX_FILE + "class1.py";
    final var class2Url = PREFIX_FILE + "class2.py";
    answers.put(7, locationLink(class1Url, class1, newRange(0, 7, 0, 13)));
    answers.put(13, locationLink(class1Url, class1, newRange(0, 13, 1, 0)));
    answers.put(21, locationLink(class2Url, class2, newRange(1, 7, 1, 13)));
    answers.put(27, locationLink(class2Url, class2, newRange(1, 13, 2, 0)));
    answers.put(33, locationLink(secondUrl, second, newRange(2, 5, 2, 11)));
    answers.put(39, locationLink(secondUrl, second, newRange(2, 11, 2, 12)));
    answers.put(55, locationLink(definitionPythonUrl, func, func));
    answers.put(59, locationLink(definitionPythonUrl, func, newRange(5, 8, 5, 9)));
    answers.put(73, locationLink(definitionPythonUrl, a, a));
    answers.put(74, locationLink(definitionPythonUrl, a, newRange(8, 1, 8, 2)));
    answers.put(77, locationLink(secondUrl, A, newRange(8, 4, 8, 5)));
    answers.put(78, locationLink(secondUrl, A, newRange(8, 5, 8, 6)));
    answers.put(81, locationLink(definitionPythonUrl, aa, aa));
    answers.put(83, locationLink(definitionPythonUrl, aa, newRange(9, 2, 9, 3)));
    answers.put(86, locationLink(definitionPythonUrl, a, newRange(9, 5, 9, 6)));
    answers.put(87, locationLink(definitionPythonUrl, a, newRange(9, 6, 10, 0)));
    answers.put(88, locationLink(definitionPythonUrl, func, newRange(10, 0, 10, 4)));
    answers.put(92, locationLink(definitionPythonUrl, func, newRange(10, 4, 10, 5)));
    answers.put(96, locationLink(definitionPythonUrl, b1, b1));
    answers.put(98, locationLink(definitionPythonUrl, b1, newRange(12, 2, 12, 3)));
    answers.put(101, locationLink(class1Url, class1, newRange(12, 5, 12, 11)));
    answers.put(107, locationLink(class1Url, class1, newRange(12, 11, 12, 12)));
    answers.put(108, locationLink(class1Url, class1B, newRange(12, 12, 12, 13)));
    answers.put(109, locationLink(class1Url, class1B, newRange(12, 13, 12, 14)));
    answers.put(112, locationLink(definitionPythonUrl, b2, b2));
    answers.put(114, locationLink(definitionPythonUrl, b2, newRange(13, 2, 13, 3)));
    answers.put(117, locationLink(class2Url, class2, newRange(13, 5, 13, 11)));
    answers.put(123, locationLink(class2Url, class2, newRange(13, 11, 13, 12)));
    answers.put(124, locationLink(class2Url, class2B, newRange(13, 12, 13, 13)));
    answers.put(125, locationLink(class2Url, class2B, newRange(13, 13, 13, 14)));
    return answers;
  }

  @Test
  public void newDefinitionJavaTest() {
    final var PREFIX = "./test-data/references/java/project-definition/src/";
    final var dirPath = Paths.get(PREFIX);
    final var files = List.of(Paths.get(PREFIX + "com/Another.java"),
        Paths.get(PREFIX + "org/Another.java"),
        Paths.get(PREFIX + "org/DefinitionJava.java"));
    final TestEngine engine = new DefinitionTestEngine(dirPath, files);
    engine.processLspTests();
    engine.copyFilesToFixture(myFixture);
    final var definitionTests = engine.getTests();
    for (final var test : definitionTests) {
      assertTrue(test instanceof DefinitionTestEngine.DefinitionTest);
      final var params = ((DefinitionTestEngine.DefinitionTest) test).getParams();
      final var answer = ((DefinitionTestEngine.DefinitionTest) test).getAnswer().getRight();

      final var path = LspPath.fromLspUri(params.getTextDocument().getUri());
      final var future = new FindDefinitionCommand(params.getPosition()).runAsync(getProject(), path);
      final var lst = TestUtil.getNonBlockingEdt(future, 50000);
      assertNotNull(lst);
      final var result = lst.getRight();

      assertEquals(answer, result);
    }
  }
}
