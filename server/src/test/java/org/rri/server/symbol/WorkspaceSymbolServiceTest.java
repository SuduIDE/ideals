package org.rri.server.symbol;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

    final var orgSomeSymbolForWorkspaceSymbolUri = LspPath.fromVirtualFile(orgSomeSymbolForWorkspaceSymbolVirtualFile).toLspUri();
    final var comSomeSymbolForWorkspaceSymbolUri = LspPath.fromVirtualFile(comSomeSymbolForWorkspaceSymbolVirtualFile).toLspUri();
    final var class2SomeSymbolForWorkspaceSymbolUri = LspPath.fromVirtualFile(class2VirtualFile).toLspUri();

    final var orgSomeSymbolForWorkspaceSymbol = workspaceSymbol("SomeSymbolForWorkspaceSymbol", SymbolKind.Class,
        location(orgSomeSymbolForWorkspaceSymbolUri, range(2, 13, 2, 41)));
    final var comSomeSymbolForWorkspaceSymbol = workspaceSymbol("SomeSymbolForWorkspaceSymbol", SymbolKind.Class,
        location(comSomeSymbolForWorkspaceSymbolUri, range(2, 13, 2, 41)));
    final var class2SomeSymbolForWorkspaceSymbol = workspaceSymbol("SomeSymbolForWorkspaceSymbol(int)", SymbolKind.Method,
        location(class2SomeSymbolForWorkspaceSymbolUri, range(3, 16, 3, 44)));
    class2SomeSymbolForWorkspaceSymbol.setContainerName("Class2");

    final var future = new WorkspaceSymbolService("SomeSymbolForWorkspaceSymbol").execute(getProject());
    final var result = TestUtil.getNonBlockingEdt(future, 30000).getRight();

    assertEquals(List.of(comSomeSymbolForWorkspaceSymbol, class2SomeSymbolForWorkspaceSymbol, orgSomeSymbolForWorkspaceSymbol), result);
  }

  @Test
  public void testWorkspaceSymbolPython() {
    final var virtualFile = myFixture.copyDirectoryToProject("python/project1", "");
    final var class1File = virtualFile.findChild("class1.py");
    assertNotNull(class1File);
    final var otherClass1File = virtualFile.findChild("otherClass1.py");
    assertNotNull(otherClass1File);
    final var workspaceSymbolFile = virtualFile.findChild("workspaceSymbol.py");
    assertNotNull(workspaceSymbolFile);

    final var class1Uri = LspPath.fromVirtualFile(class1File).toLspUri();
    final var otherClass1Uri = LspPath.fromVirtualFile(otherClass1File).toLspUri();
    final var workspaceSymbolUri = LspPath.fromVirtualFile(workspaceSymbolFile).toLspUri();

    final var class1Class1 = workspaceSymbol("Class1", SymbolKind.Class,
        location(class1Uri, range(0, 6, 0, 12)));
    final var otherClass1Class1 = workspaceSymbol("Class1", SymbolKind.Class,
        location(otherClass1Uri, range(0, 6, 0, 12)));
    final var workspaceSymbolVarClass1 = workspaceSymbol("Class1", SymbolKind.Variable,
        location(workspaceSymbolUri, range(0, 0, 0, 6)));
    final var workspaceSymbolFuncClass1 = workspaceSymbol("Class1(x, y)", SymbolKind.Function,
        location(workspaceSymbolUri, range(2, 4, 2, 10)));

    final var future = new WorkspaceSymbolService("Class1").execute(getProject());
    final var result = TestUtil.getNonBlockingEdt(future, 30000).getRight();

    assertEquals(List.of(workspaceSymbolFuncClass1, workspaceSymbolVarClass1, otherClass1Class1, class1Class1), result);
  }

  @Test
  public void testWorkspaceSymbolKotlin() {
    var virtualFile = myFixture.copyDirectoryToProject("kotlin/project1/src", "");
    virtualFile = virtualFile.findChild("org");
    assertNotNull(virtualFile);
    virtualFile = virtualFile.findChild("WorkspaceSymbol.kt");
    assertNotNull(virtualFile);
    final var uri = LspPath.fromVirtualFile(virtualFile).toLspUri();

    final var varSymbol = workspaceSymbol("symbol", SymbolKind.Field,
        location(uri, range(3, 14, 3, 20)), "WorkspaceSymbol");
    final var funSymbol = workspaceSymbol("symbol(int, int)", SymbolKind.Function,
        location(uri, range(5, 6, 5, 12)), "WorkspaceSymbol");

    final var future = new WorkspaceSymbolService("symbol").execute(getProject());
    final var result = TestUtil.getNonBlockingEdt(future, 30000).getRight();

    assertEquals(List.of(varSymbol, funSymbol), result);
  }

  @NotNull
  private static WorkspaceSymbol workspaceSymbol(@NotNull String name, @NotNull SymbolKind kind, @NotNull Location location) {
    return workspaceSymbol(name, kind, location, null);
  }

  @NotNull
  private static WorkspaceSymbol workspaceSymbol(@NotNull String name, @NotNull SymbolKind kind, @NotNull Location location,
                                                 @Nullable String containerName) {
    return new WorkspaceSymbol(name, kind, Either.forLeft(location), containerName);
  }

  @NotNull
  private static Location location(@NotNull String uri, @NotNull Range range) {
    return new Location(uri, range);
  }

  @SuppressWarnings("SameParameterValue")
  @NotNull
  private static Range range(int line1, int char1, int line2, int char2) {
    return new Range(new Position(line1, char1), new Position(line2, char2));
  }
}
