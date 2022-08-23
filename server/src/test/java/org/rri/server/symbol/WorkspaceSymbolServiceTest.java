package org.rri.server.symbol;

import com.intellij.openapi.project.Project;
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
import java.util.Random;

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

    final var orgSomeSymbolForWorkspaceSymbolUri = normalizeUri(LspPath.fromVirtualFile(orgSomeSymbolForWorkspaceSymbolVirtualFile).toLspUri());
    final var comSomeSymbolForWorkspaceSymbolUri = normalizeUri(LspPath.fromVirtualFile(comSomeSymbolForWorkspaceSymbolVirtualFile).toLspUri());
    final var class2SomeSymbolForWorkspaceSymbolUri = normalizeUri(LspPath.fromVirtualFile(class2VirtualFile).toLspUri());

    final var orgSomeSymbolForWorkspaceSymbol = workspaceSymbol("SomeSymbolForWorkspaceSymbol", SymbolKind.Class,
        workspaceSymbolLocation(orgSomeSymbolForWorkspaceSymbolUri));
    final var comSomeSymbolForWorkspaceSymbol = workspaceSymbol("SomeSymbolForWorkspaceSymbol", SymbolKind.Class,
        workspaceSymbolLocation(comSomeSymbolForWorkspaceSymbolUri));
    final var class2SomeSymbolForWorkspaceSymbol = workspaceSymbol("SomeSymbolForWorkspaceSymbol(int)", SymbolKind.Method,
        workspaceSymbolLocation(class2SomeSymbolForWorkspaceSymbolUri));
    class2SomeSymbolForWorkspaceSymbol.setContainerName("Class2");

    final var result = doSearch("SomeSymbolForWorkspaceSymbol", getProject());
    final var answer = List.of(comSomeSymbolForWorkspaceSymbol, class2SomeSymbolForWorkspaceSymbol, orgSomeSymbolForWorkspaceSymbol);

    assertEquals(answer, result);

    checkResolves(result, answer,
        List.of(range(2, 13, 2, 41),
            range(3, 16, 3, 44),
            range(2, 13, 2, 41)),
        getProject());
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

    final var class1Uri = normalizeUri(LspPath.fromVirtualFile(class1File).toLspUri());
    final var otherClass1Uri = normalizeUri(LspPath.fromVirtualFile(otherClass1File).toLspUri());
    final var workspaceSymbolUri = normalizeUri(LspPath.fromVirtualFile(workspaceSymbolFile).toLspUri());

    final var class1Class1 = workspaceSymbol("Class1", SymbolKind.Class,
        workspaceSymbolLocation(class1Uri));
    final var otherClass1Class1 = workspaceSymbol("Class1", SymbolKind.Class,
        workspaceSymbolLocation(otherClass1Uri));
    final var workspaceSymbolVarClass1 = workspaceSymbol("Class1", SymbolKind.Variable,
        workspaceSymbolLocation(workspaceSymbolUri));
    final var workspaceSymbolFuncClass1 = workspaceSymbol("Class1(x, y)", SymbolKind.Function,
        workspaceSymbolLocation(workspaceSymbolUri));

    final var result = doSearch("Class1", getProject());
    final var answer = List.of(workspaceSymbolFuncClass1, workspaceSymbolVarClass1, otherClass1Class1, class1Class1);
    assertEquals(answer, result);

    checkResolves(result, answer,
        List.of(range(2, 4, 2, 10),
            range(0, 0, 0, 6),
            range(0, 6, 0, 12),
            range(0, 6, 0, 12)),
        getProject());
  }

  @Test
  public void testWorkspaceSymbolKotlin() {
    var virtualFile = myFixture.copyDirectoryToProject("kotlin/project1/src", "");
    virtualFile = virtualFile.findChild("org");
    assertNotNull(virtualFile);
    virtualFile = virtualFile.findChild("WorkspaceSymbol.kt");
    assertNotNull(virtualFile);
    final var uri = normalizeUri(LspPath.fromVirtualFile(virtualFile).toLspUri());

    final var varSymbol = workspaceSymbol("symbol", SymbolKind.Field,
        workspaceSymbolLocation(uri), "WorkspaceSymbol");
    final var funSymbol = workspaceSymbol("symbol(int, int)", SymbolKind.Function,
        workspaceSymbolLocation(uri), "WorkspaceSymbol");

    final var result = doSearch("symbol", getProject());
    final var answer = List.of(varSymbol, funSymbol);

    assertEquals(answer, result);

    checkResolves(result, answer,
        List.of(range(3, 14, 3, 20),
            range(5, 6, 5, 12)),
        getProject());
  }

  @NotNull
  private static WorkspaceSymbol workspaceSymbol(@NotNull String name, @NotNull SymbolKind kind, @NotNull WorkspaceSymbolLocation location) {
    return workspaceSymbol(name, kind, location, null);
  }

  @NotNull
  private static WorkspaceSymbol workspaceSymbol(@NotNull String name, @NotNull SymbolKind kind, @NotNull WorkspaceSymbolLocation location,
                                                 @Nullable String containerName) {
    return new WorkspaceSymbol(name, kind, Either.forRight(location), containerName);
  }

  @NotNull
  private static WorkspaceSymbolLocation workspaceSymbolLocation(@NotNull String uri) {
    return new WorkspaceSymbolLocation(uri);
  }

  @NotNull
  private static String normalizeUri(@NotNull String uri) {
    return uri.replaceAll("/+", "/");
  }

  @SuppressWarnings("SameParameterValue")
  @NotNull
  private static Range range(int line1, int char1, int line2, int char2) {
    return new Range(new Position(line1, char1), new Position(line2, char2));
  }

  @NotNull
  private static List<? extends WorkspaceSymbol> doSearch(@NotNull String pattern, @NotNull Project project) {
    final var service = project.getService(WorkspaceSymbolService.class);
    assertNotNull(service);
    return TestUtil.getNonBlockingEdt(service.runSearch(pattern), 3000).getRight();
  }

  private static void checkResolves(@NotNull List<? extends WorkspaceSymbol> symbols,
                                    @NotNull List<WorkspaceSymbol> answer,
                                    @NotNull List<@NotNull Range> ranges,
                                    @NotNull Project project) {
    assertEquals(answer.size(), ranges.size());
    for (int i = 0; i < answer.size(); i++) {
      final var sym = answer.get(i);
      final var range = ranges.get(i);
      var uri = new StringBuffer(sym.getLocation().getRight().getUri()).insert(6, "//").toString();
      sym.setLocation(Either.forLeft(new Location(uri, range)));
    }
    final var service = project.getService(WorkspaceSymbolService.class);
    assertNotNull(service);
    final var ind = new Random().nextInt(symbols.size());
    final var sym = TestUtil.getNonBlockingEdt(service.resolveWorkspaceSymbol(symbols.get(ind)), 30000);
    assertEquals(answer.get(ind), sym);
  }
}
