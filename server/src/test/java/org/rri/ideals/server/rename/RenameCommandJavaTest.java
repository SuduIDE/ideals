package org.rri.ideals.server.rename;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.RenameFile;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.ideals.server.LspPath;

import java.nio.file.Paths;
import java.util.List;

import static org.rri.ideals.server.TestUtil.newTextEdit;

@RunWith(JUnit4.class)
public class RenameCommandJavaTest extends RenameCommandTestBase {
  @Override
  protected String getTestDataPath() {
    return Paths.get("test-data/rename").toAbsolutePath().toString();
  }

  @Before
  public void copyDirectoryToProjectAndSetPaths() {
    final var projectFile = myFixture.copyDirectoryToProject("java/project1/src", "");
    final var virtualFileRenameTest = projectFile.findChild("RenameTest.java");
    assertNotNull(virtualFileRenameTest);
    var virtualFileOrgTestClass = projectFile.findChild("org");
    assertNotNull(virtualFileOrgTestClass);
    virtualFileOrgTestClass = virtualFileOrgTestClass.findChild("TestClass.java");
    assertNotNull(virtualFileOrgTestClass);

    renameTestPath = LspPath.fromVirtualFile(virtualFileRenameTest);
    renameTestUri = renameTestPath.toLspUri();
    orgTestClassUri = LspPath.fromVirtualFile(virtualFileOrgTestClass).toLspUri();
  }

  @Test
  public void testRenameVariable() {
    final var newName = "abcd";

    final var edits = List.of(
        textDocumentEdit(orgTestClassUri, list(
            newTextEdit(17, 19, 17, 22, newName),
            newTextEdit(22, 9, 22, 12, newName)
        )),
        textDocumentEdit(renameTestUri, list(newTextEdit(12, 19, 12, 22, newName)))
    );

    final var pos = new Position(12, 20);
    checkRename(edits, pos, newName);
  }

  @Test
  public void testRenameFunction() {
    final var newName = "fooBar";

    final var edits = List.of(
        textDocumentEdit(orgTestClassUri, list(
            newTextEdit(19, 14, 19, 17, newName),
            newTextEdit(23, 4, 23, 7, newName)
        )),
        textDocumentEdit(renameTestUri, list(newTextEdit(13, 9, 13, 12, newName)))
    );

    final var pos = new Position(13, 10);
    checkRename(edits, pos, newName);
  }

  @Test
  public void testRenameClass() {
    final var newName = "Inner111";

    final var answer = List.of(
        textDocumentEdit(orgTestClassUri, list(
            newTextEdit(7, 22, 7, 28, newName),
            newTextEdit(9, 11, 9, 17, newName)
        )),
        textDocumentEdit(renameTestUri, list(
            newTextEdit(5, 14, 5, 20, newName),
            newTextEdit(5, 41, 5, 47, newName),
            newTextEdit(6, 14, 6, 20, newName),
            newTextEdit(6, 48, 6, 54, newName)
        ))
    );

    var pos = new Position(5, 15);
    checkRename(answer, pos, newName);

    pos = new Position(5, 42);
    checkRename(answer, pos, newName);
  }
  
  @Test
  @Ignore // TODO fails because we're not able yet to rename both a class and its file
  public void testRenameClassWithFileEdit() {
    final var newName = "TestClassNext";

    final var documentEdits = List.of(
        textDocumentEdit(orgTestClassUri, list(
            newTextEdit(6, 13, 6, 22, newName),
            newTextEdit(21, 9, 21, 18, newName)
        )),
        textDocumentEdit(renameTestUri, list(
            newTextEdit(0, 11, 0, 20, newName),
            newTextEdit(4, 4, 4, 13, newName),
            newTextEdit(4, 25, 4, 34, newName),
            newTextEdit(5, 4, 5, 13, newName),
            newTextEdit(5, 31, 5, 40, newName),
            newTextEdit(6, 4, 6, 13, newName),
            newTextEdit(6, 38, 6, 47, newName)
        ))
    );

    final var operations = List.of(new RenameFile(orgTestClassUri, orgTestClassUri.replace("TestClass", newName)));

    final var pos = new Position(4, 5);
    checkRename(documentEdits, pos, newName, operations);
  }
}
