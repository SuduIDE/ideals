package org.rri.ideals.server.rename;

import org.eclipse.lsp4j.Position;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.ideals.server.LspPath;

import java.nio.file.Paths;
import java.util.List;

import static org.rri.ideals.server.TestUtil.newTextEdit;

@RunWith(JUnit4.class)
public class RenameCommandPythonTest extends RenameCommandTestBase {
  @Override
  protected String getTestDataPath() {
    return Paths.get("test-data/rename").toAbsolutePath().toString();
  }

  @Before
  public void copyDirectoryToProjectAndSetPaths() {
    final var projectFile = myFixture.copyDirectoryToProject("python/project1", "");
    final var virtualFileRenameTest = projectFile.findChild("rename_test.py");
    assertNotNull(virtualFileRenameTest);

    final var virtualFileTestClass = projectFile.findChild("file1.py");
    assertNotNull(virtualFileTestClass);

    renameTestPath = LspPath.fromVirtualFile(virtualFileRenameTest);
    renameTestUri = renameTestPath.toLspUri();
    orgTestClassUri = LspPath.fromVirtualFile(virtualFileTestClass).toLspUri();
  }

  @Test
  public void testRenameVariable() {
    final var newName = "next";

    final var edits = List.of(
        textDocumentEdit(renameTestUri, list(
            newTextEdit(11, 12, 11, 15, newName)
        )),
        textDocumentEdit(orgTestClassUri, list(
            newTextEdit(18, 13, 18, 16, newName)
        ))
    );

    final var pos = new Position(11, 13);
    checkRename(edits, pos, newName);
  }

  @Test
  public void testRenameFunction() {
    final var newName = "fooBar";

    final var edits = List.of(
        textDocumentEdit(renameTestUri, list(
            newTextEdit(12, 5, 12, 8, newName)
        )),
        textDocumentEdit(orgTestClassUri, list(
            newTextEdit(14, 8, 14, 11, newName),
            newTextEdit(19, 13, 19, 16, newName)
        ))
    );

    final var pos = new Position(12, 6);
    checkRename(edits, pos, newName);
  }

  @Test
  public void testRenameClass() {
    final var newName = "inner111";

    final var answer = List.of(
        textDocumentEdit(renameTestUri, list(
            newTextEdit(4, 17, 4, 23, newName),
            newTextEdit(5, 17, 5, 23, newName)
        )),
        textDocumentEdit(orgTestClassUri, list(
            newTextEdit(6, 10, 6, 16, newName)
        ))
    );

    var pos = new Position(4, 20);
    checkRename(answer, pos, newName);
  }
}
