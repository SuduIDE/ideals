package org.rri.ideals.server.rename;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.TestUtil;

import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.rri.ideals.server.TestUtil.newTextEdit;

@RunWith(JUnit4.class)
public class RenameCommandJavaTest extends BasePlatformTestCase {
  @NotNull
  private LspPath renameTestPath;
  @NotNull
  private String renameTestUri;
  @NotNull
  private String orgTestClassUri;

  @NotNull
  private static final Comparator<TextEdit> comparator = Comparator
      .<TextEdit>comparingInt(edit -> edit.getRange().getStart().getLine())
      .thenComparingInt(edit -> edit.getRange().getStart().getCharacter());

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

    final var inner1ClsTextEdit = newTextEdit(5, 14, 5, 20, newName);
    final var inner1CnstrTextEdit = newTextEdit(5, 41, 5, 47, newName);
    final var nestedInner1ClsTextEdit = newTextEdit(6, 14, 6, 20, newName);
    final var nestedInner1CnstrTextEdit = newTextEdit(6, 48, 6, 54, newName);

    final var orgTestClassEdits = textDocumentEdit(orgTestClassUri, list(
        newTextEdit(7, 22, 7, 28, newName),
        newTextEdit(9, 11, 9, 17, newName)
    ));

    var answer = List.of(
        orgTestClassEdits,
        textDocumentEdit(renameTestUri, list(
            inner1ClsTextEdit,
            inner1CnstrTextEdit,
            nestedInner1ClsTextEdit,
            nestedInner1CnstrTextEdit
        ))
    );

    var pos = new Position(5, 15);
    checkRename(answer, pos, newName);

    pos = new Position(5, 42);
    checkRename(answer, pos, newName);
  }

  @NotNull
  private TextDocumentEdit textDocumentEdit(@NotNull String uri, @NotNull List<@NotNull TextEdit> edits) {
    return new TextDocumentEdit(new VersionedTextDocumentIdentifier(uri, 1), edits);
  }

  // RenameCommand returns ListN, but List.of can be List12 => Comparison Failure
  @NotNull
  private List<@NotNull TextEdit> list(@NotNull TextEdit... textEdits) {
    return Stream.of(textEdits).toList();
  }

  private void checkRename(@NotNull List<@NotNull TextDocumentEdit> edits, @NotNull Position pos, @NotNull String newName) {
    final var answer = new WorkspaceEdit(edits.stream().map(Either::<TextDocumentEdit, ResourceOperation>forLeft).toList());

    final var future = new RenameCommand(pos, newName).runAsync(getProject(), renameTestPath);
    final var result = TestUtil.getNonBlockingEdt(future, 50000);

    assertNotNull(result);

    result.getDocumentChanges().forEach(either -> {
      final var docEdit = either.getLeft();
      final var sortedEdits = docEdit.getEdits().stream().sorted(comparator).toList();
      docEdit.setEdits(sortedEdits);
    });

    assertEquals(answer, result);
  }
}
