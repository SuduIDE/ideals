package org.rri.server.symbol;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.server.LspPath;
import org.rri.server.TestUtil;

import java.lang.String;
import java.nio.file.Paths;
import java.util.List;

import static org.eclipse.lsp4j.SymbolKind.Boolean;
import static org.eclipse.lsp4j.SymbolKind.Class;
import static org.eclipse.lsp4j.SymbolKind.Enum;
import static org.eclipse.lsp4j.SymbolKind.Module;
import static org.eclipse.lsp4j.SymbolKind.Number;
import static org.eclipse.lsp4j.SymbolKind.Package;
import static org.eclipse.lsp4j.SymbolKind.String;
import static org.eclipse.lsp4j.SymbolKind.*;

@RunWith(JUnit4.class)
public class DocumentSymbolCommandTest extends BasePlatformTestCase {

  @Override
  protected String getTestDataPath() {
    return Paths.get("test-data/symbol").toAbsolutePath().toString();
  }

  @Test
  public void testDocumentSymbolJava() {
    var virtualFile = myFixture.copyDirectoryToProject("java/project1/src", "");
    virtualFile = virtualFile.findChild("org");
    assertNotNull(virtualFile);
    final var answers = List.of(
        documentSymbol("DocumentSymbol.java", File, range(0, 0, 28, 1)),
        documentSymbol("org", Package, range(0, 0, 0 , 12)),
        documentSymbol("com.Class1", Module, range(2, 0, 2, 18)),
        documentSymbol("DocumentSymbol", Class, range(4, 13, 4, 27)),
        documentSymbol("x", Field, range(5, 14, 5, 15)),
        documentSymbol("cls", Constant, range(6, 30, 6, 33)),
        documentSymbol("DocumentSymbol(int)", Constructor, range(8, 9, 8, 23)),
        documentSymbol("x", Variable, range(8, 28, 8, 29)),
        documentSymbol("@Override", Property, range(12, 2, 12, 11)),
        documentSymbol("foo(int, String)", Method, range(13, 13, 13, 16)),
        documentSymbol("x", Variable, range(13, 21, 13, 22)),
        documentSymbol("str", Variable, range(13, 31, 13, 34)),
        documentSymbol("a", Variable, range(14, 8, 14, 9)),
        documentSymbol("1", Number, range(14, 12, 14, 13)),
        documentSymbol("project", Variable, range(15, 11, 15, 18)),
        documentSymbol("\"lsp\"", String, range(15, 21, 15, 26)),
        documentSymbol("cls", Variable, range(16, 10, 16, 13)),
        documentSymbol("null", Null, range(16, 16, 16, 20)),
        documentSymbol("b", Variable, range(17, 12, 17, 13)),
        documentSymbol("true", Boolean, range(17, 16, 17, 20)),
        documentSymbol("EntryInter", Interface, range(21, 19, 21, 29)),
        documentSymbol("foo()", Method, range(22, 9, 22, 12)),
        documentSymbol("Litter", Enum, range(25, 14, 25, 20)),
        documentSymbol("A", EnumMember, range(26, 4, 26, 5)),
        documentSymbol("B", EnumMember, range(26, 7, 26, 8))
    );
    checkDocumentSymbols(answers, virtualFile.findChild("DocumentSymbol.java"));
  }

  @Test
  public void testDocumentSymbolPython() {
    final var virtualFile = myFixture.copyDirectoryToProject("python/project1", "");
    final var answers = List.of(
        documentSymbol("documentSymbol.py", File, range(0, 0, 24, 0)),
        documentSymbol("class1", Module, range(0, 0, 0, 13)),
        documentSymbol("class2", Module, range(1, 0, 1, 21)),
        documentSymbol("cls2", Variable, range(1, 17, 1, 21)),
        documentSymbol("from funcs import *", Module, range(2, 0, 2, 19)),
        documentSymbol("p", Variable, range(4, 0, 4, 1)),
        documentSymbol("\"lsp\"", String, range(4, 4, 4, 9)),
        documentSymbol("b", Variable, range(5, 0, 5, 1)),
        documentSymbol("True", Boolean, range(5, 4, 5, 8)),
        documentSymbol("Document_symbol", Class, range(9, 6, 9, 21)),
        documentSymbol("__init__(self)", Constructor, range(10, 8, 10, 16)),
        documentSymbol("self", Field, range(10, 17, 10, 21)),
        documentSymbol("x", Field, range(11, 13, 11, 14)),
        documentSymbol("1", Number, range(11, 17, 11, 18)),
        documentSymbol("__const", Constant, range(12, 13, 12, 20)),
        documentSymbol("None", Null, range(12, 23, 12, 27)),
        documentSymbol("foo(self, x, y)", Method, range(14, 8, 14, 11)),
        documentSymbol("self", Field, range(14, 12, 14, 16)),
        documentSymbol("x", Variable, range(14, 18, 14, 19)),
        documentSymbol("y", Variable, range(14, 21, 14, 22)),
        documentSymbol("bar(self, *args, **kwargs)", Method, range(17, 8, 17, 11)),
        documentSymbol("self", Field, range(17, 12, 17, 16)),
        documentSymbol("*args", Variable, range(17, 19, 17, 23)),
        documentSymbol("**kwargs", Variable, range(17, 27, 17, 33)),
        documentSymbol("@do_twice", Property, range(21, 0, 21, 9)),
        documentSymbol("foo_bar(x, /, y, *, z)", Function, range(22, 4, 22, 11)),
        documentSymbol("x", Variable, range(22, 12, 22, 13)),
        documentSymbol("y", Variable, range(22, 18, 22, 19)),
        documentSymbol("z", Variable, range(22, 24, 22, 25))
    );
    checkDocumentSymbols(answers, virtualFile.findChild("documentSymbol.py"));
  }

  private void checkDocumentSymbols(@NotNull List<@NotNull DocumentSymbol> answers, @Nullable VirtualFile virtualFile) {
    assertNotNull(virtualFile);
    final var path = LspPath.fromVirtualFile(virtualFile);
    final var future = new DocumentSymbolCommand().runAsync(getProject(), path);
    final var lstEither = TestUtil.getNonBlockingEdt(future, 50000);
    assertNotNull(lstEither);
    final var result = lstEither.stream().map(Either::getRight).toList();
    assertEquals(answers, result);
  }

  @NotNull
  private static DocumentSymbol documentSymbol(@NotNull String name, @Nullable SymbolKind kind, @NotNull Range range) {
    return new DocumentSymbol(name, kind, range, range);
  }

  @SuppressWarnings("SameParameterValue")
  @NotNull
  private static Range range(int line1, int char1, int line2, int char2) {
    return new Range(new Position(line1, char1), new Position(line2, char2));
  }
}
