package org.rri.server.references;

import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightKind;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.server.LspPath;
import org.rri.server.TestUtil;

import java.util.HashSet;
import java.util.Set;

@RunWith(JUnit4.class)
public class DocumentHighlightCommandTest extends ReferencesCommandTestBase {
  @Test
  public void testDocumentHighlightJava() {
    var virtualFile = myFixture.copyDirectoryToProject("java/project1/src", "");
    virtualFile = virtualFile.findChild("DocumentHighlightTest.java");
    assertNotNull(virtualFile);
    final var path = LspPath.fromVirtualFile(virtualFile);

    // Test variable
    var answers = Set.of(
        new DocumentHighlight(range(1, 14, 1, 17), DocumentHighlightKind.Text),
        new DocumentHighlight(range(13, 4, 13, 7), DocumentHighlightKind.Write));
    checkHighlight(answers, new Position(1, 14), path);

    // Test method
    answers = Set.of(
        new DocumentHighlight(range(6, 21, 6, 24), DocumentHighlightKind.Text),
        new DocumentHighlight(range(14, 4, 14, 7), DocumentHighlightKind.Text));
    checkHighlight(answers, new Position(14, 4), path);

    // Test class
    answers = Set.of(
        new DocumentHighlight(range(2, 14, 2, 26), DocumentHighlightKind.Read),
        new DocumentHighlight(range(8, 18, 8, 30), DocumentHighlightKind.Read));
    checkHighlight(answers, new Position(2, 14), path);
  }

  @Test
  public void testDocumentHighlightPython() {
    var virtualFile = myFixture.copyDirectoryToProject("python/project1", "");
    virtualFile = virtualFile.findChild("documentHighlightTest.py");
    assertNotNull(virtualFile);
    final var path = LspPath.fromVirtualFile(virtualFile);

    // Test variable
    var answers = Set.of(
        new DocumentHighlight(range(8, 0, 8, 1), DocumentHighlightKind.Write),
        new DocumentHighlight(range(9, 4, 9, 5), DocumentHighlightKind.Read));
    checkHighlight(answers, new Position(8, 0), path);

    // Test method
    answers = Set.of(
        new DocumentHighlight(range(4, 4, 4, 7), DocumentHighlightKind.Text),
        new DocumentHighlight(range(13, 0, 13, 3), DocumentHighlightKind.Text));
    checkHighlight(answers, new Position(13, 0), path);

    // Test class
    answers = Set.of(new DocumentHighlight(range(15, 5, 15, 14), DocumentHighlightKind.Text));
    checkHighlight(answers, new Position(15, 5), path);
  }

  private void checkHighlight(@NotNull Set<@NotNull DocumentHighlight> answers,
                                @NotNull Position pos,
                                @NotNull LspPath path) {
    final var future = new DocumentHighlightCommand(pos).runAsync(getProject(), path);
    final var lst = TestUtil.getNonBlockingEdt(future, 50000);
    assertNotNull(lst);
    assertEquals(answers, new HashSet<>(lst));
  }
}
