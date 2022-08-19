package org.rri.server.symbol;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.server.LspPath;
import org.rri.server.TestUtil;

import java.nio.file.Paths;
import java.util.List;

@RunWith(JUnit4.class)
public class WorkspaceSymbolServiceTest extends BasePlatformTestCase {

  @Override
  protected String getTestDataPath() {
    return Paths.get("test-data/symbol").toAbsolutePath().toString();
  }

  @Test
  public void testWorkspaceSymbolJava() {
    var virtualFile = myFixture.copyDirectoryToProject("java/project1/src", "");
    final var orgVirtualFile = virtualFile.findChild("org");
    assertNotNull(orgVirtualFile);
    final var comVirtualFile = virtualFile.findChild("com");
    assertNotNull(comVirtualFile);
    final var orgSomeSymbolForWorkspaceSymbolVirtualFile = orgVirtualFile.findChild("SomeSymbolForWorkspaceSymbol.java");
    assertNotNull(orgSomeSymbolForWorkspaceSymbolVirtualFile);
    final var comSomeSymbolForWorkspaceSymbolVirtualFile = comVirtualFile.findChild("SomeSymbolForWorkspaceSymbol.java");
    assertNotNull(comSomeSymbolForWorkspaceSymbolVirtualFile);
    final var class2VirtualFile = orgVirtualFile.findChild("Class2.java");
    assertNotNull(class2VirtualFile);

    final var orgSomeSymbolForWorkspaceSymbolPath = LspPath.fromVirtualFile(orgSomeSymbolForWorkspaceSymbolVirtualFile);
    final var comSomeSymbolForWorkspaceSymbolPath = LspPath.fromVirtualFile(comSomeSymbolForWorkspaceSymbolVirtualFile);
    final var class2SomeSymbolForWorkspaceSymbolPath = LspPath.fromVirtualFile(class2VirtualFile);

    final var orgSomeSymbolForWorkspaceSymbol = workspaceSymbol("SomeSymbolForWorkspaceSymbol", SymbolKind.Class,
        location(orgSomeSymbolForWorkspaceSymbolPath.toLspUri(), range(2, 13, 2, 41)));
    final var comSomeSymbolForWorkspaceSymbol = workspaceSymbol("SomeSymbolForWorkspaceSymbol", SymbolKind.Class,
        location(comSomeSymbolForWorkspaceSymbolPath.toLspUri(), range(2, 13, 2, 41)));
    final var class2SomeSymbolForWorkspaceSymbol = workspaceSymbol("SomeSymbolForWorkspaceSymbol(int)", SymbolKind.Method,
        location(class2SomeSymbolForWorkspaceSymbolPath.toLspUri(), range(3, 16, 3, 44)));
    class2SomeSymbolForWorkspaceSymbol.setContainerName("SomeSymbolForWorkspaceSymbol");

    final var future = new WorkspaceSymbolService("SomeSymbolForWorkspaceSymbol").execute(getProject());
    final var result = TestUtil.getNonBlockingEdt(future, 30000).getRight();

    assertEquals(List.of(comSomeSymbolForWorkspaceSymbol, class2SomeSymbolForWorkspaceSymbol, orgSomeSymbolForWorkspaceSymbol), result);
  }

  @NotNull
  private static WorkspaceSymbol workspaceSymbol(String name, SymbolKind kind, Location location) {
    return new WorkspaceSymbol(name, kind, Either.forLeft(location));
  }

  @NotNull
  private static Location location(String uri, Range range) {
    return new Location(uri, range);
  }

  @SuppressWarnings("SameParameterValue")
  @NotNull
  private static Range range(int line1, int char1, int line2, int char2) {
    return new Range(new Position(line1, char1), new Position(line2, char2));
  }
}
