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

import static org.eclipse.lsp4j.SymbolKind.Class;
import static org.eclipse.lsp4j.SymbolKind.Enum;
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

    final var fieldIntX = documentSymbol("x", Field, range(5, 14, 5, 15));
    final var fieldClass1Cls = documentSymbol("cls", Constant, range(6, 30, 6, 33));

    final var constructorParamX = documentSymbol("x", Variable, range(8, 28, 8, 29));
    final var DSConstructor = documentSymbol("DocumentSymbol(int)", Constructor, range(8, 9, 8, 23),
        List.of(constructorParamX));

    final var paramFooX = documentSymbol("x", Variable, range(13, 21, 13, 22));
    final var paramFooStr = documentSymbol("str", Variable, range(13, 31, 13, 34));

    final var varFooA = documentSymbol("a", Variable, range(14, 8, 14, 9));

    final var varFooProject = documentSymbol("project", Variable, range(15, 11, 15, 18));

    final var classFooCls = documentSymbol("cls", Variable, range(16, 10, 16, 13));

    final var varFooB = documentSymbol("b", Variable, range(17, 12, 17, 13));

    final var methodFoo = documentSymbol("foo(int, String)", Method, range(13, 13, 13, 16),
        List.of(paramFooX, paramFooStr, varFooA, varFooProject, classFooCls, varFooB));

    final var interMethodFoo = documentSymbol("foo()", Method, range(22, 9, 22, 12));
    final var entryInterface = documentSymbol("EntryInter", Interface, range(21, 19, 21, 29),
        List.of(interMethodFoo));

    final var enumMemberA = documentSymbol("A", EnumMember, range(26, 4, 26, 5));
    final var enumMemberB = documentSymbol("B", EnumMember, range(26, 7, 26, 8));
    final var enumLetter = documentSymbol("Letter", Enum, range(25, 14, 25, 20),
        List.of(enumMemberA, enumMemberB));

    final var DSClass = documentSymbol("DocumentSymbol", Class, range(4, 13, 4, 27),
        List.of(fieldIntX, fieldClass1Cls, DSConstructor, methodFoo, entryInterface, enumLetter));

    final var answers = List.of(DSClass);
    checkDocumentSymbols(answers, virtualFile.findChild("DocumentSymbol.java"));
  }

  @Test
  public void testDocumentSymbolPython() {
    final var virtualFile = myFixture.copyDirectoryToProject("python/project1", "");

    final var asCls2 = documentSymbol("cls2", Variable, range(1, 17, 1, 21));

    final var varStringP = documentSymbol("p", Variable, range(4, 0, 4, 1));

    final var barBoolB = documentSymbol("b", Variable, range(5, 0, 5, 1));

    final var DSVarX = documentSymbol("x", Field, range(11, 13, 11, 14));
    final var DSVar__const = documentSymbol("__CONST", Constant, range(12, 13, 12, 20));
    final var DSConstructor = documentSymbol("__init__(self)", Constructor, range(10, 8, 10, 16),
        List.of(DSVarX, DSVar__const));

    final var fooParamX = documentSymbol("x", Variable, range(14, 18, 14, 19));
    final var fooParamY = documentSymbol("y", Variable, range(14, 21, 14, 22));
    final var methodFoo = documentSymbol("foo(self, x, y)", Method, range(14, 8, 14, 11),
        List.of(fooParamX, fooParamY));

    final var barParamArgs = documentSymbol("*args", Variable, range(17, 19, 17, 23));
    final var barParamKwargs = documentSymbol("**kwargs", Variable, range(17, 27, 17, 33));
    final var methodBar = documentSymbol("bar(self, *args, **kwargs)", Method, range(17, 8, 17, 11),
        List.of(barParamArgs, barParamKwargs));

    final var DSClass = documentSymbol("Document_symbol", Class, range(9, 6, 9, 21),
        List.of(DSConstructor, methodFoo, methodBar));

    final var varFooBarX = documentSymbol("x", Variable, range(22, 12, 22, 13));
    final var varFooBarY = documentSymbol("y", Variable, range(22, 18, 22, 19));
    final var varFooBarZ = documentSymbol("z", Variable, range(22, 24, 22, 25));
    final var functionFooBar = documentSymbol("foo_bar(x, /, y, *, z)", Function, range(22, 4, 22, 11),
        List.of(varFooBarX, varFooBarY, varFooBarZ));

    final var answers = List.of(asCls2, varStringP, barBoolB, DSClass, functionFooBar);
    checkDocumentSymbols(answers, virtualFile.findChild("documentSymbol.py"));
  }

  @Test
  public void testDocumentSymbolKotlin() {
    var virtualFile = myFixture.copyDirectoryToProject("kotlin/project1/src", "");
    virtualFile = virtualFile.findChild("org");
    assertNotNull(virtualFile);

    final var enumMemberA = documentSymbol("A", EnumMember, range(5, 2, 5, 3));
    final var enumMemberB = documentSymbol("B", EnumMember, range(5, 5, 5, 6));
    final var enumLetters = documentSymbol("Letter", Enum, range(4, 11, 4, 17),
        List.of(enumMemberA, enumMemberB));

    final var interFooParamX = documentSymbol("x", Variable, range(9, 10, 9, 11));
    final var interFooParamStr = documentSymbol("str", Variable, range(9, 18, 9, 21));
    final var interMethodFoo = documentSymbol("foo(Int, String)", Method, range(9, 6, 9, 9),
        List.of(interFooParamX, interFooParamStr));
    final var interInterface = documentSymbol("Interface", Interface, range(8, 10, 8, 19),
        List.of(interMethodFoo));

    final var annotationClassForTest = documentSymbol("ForTest", Class, range(12, 17, 12, 24));

    final var constructorParamX = documentSymbol("x", Variable, range(14, 35, 14, 36));
    final var DSConstructor = documentSymbol("DocumentSymbol(Int)", Constructor, range(14, 34, 14, 42),
        List.of(constructorParamX));
    final var DSClassFieldX = documentSymbol("x", Field, range(15, 14, 15, 15));
    final var DSCLassFieldCls = documentSymbol("cls", Field, range(16, 14, 16, 17));

    final var fooParamX = documentSymbol("x", Variable, range(18, 19, 18, 20));
    final var fooParamStr = documentSymbol("str", Variable, range(18, 27, 18, 30));

    final var fooVarA = documentSymbol("a", Variable, range(19, 8, 19, 9));

    final var fooVarCls = documentSymbol("cls", Variable, range(20, 8, 20, 11));

    final var fooVarB = documentSymbol("b", Variable, range(21, 8, 21, 9));

    final var methodFoo = documentSymbol("foo(Int, String)", Method, range(18, 15, 18, 18),
        List.of(fooParamX, fooParamStr, fooVarA, fooVarCls, fooVarB));

    final var methodBar = documentSymbol("bar()", Method, range(25, 6, 25, 9));

    final var DSClass = documentSymbol("DocumentSymbol", Class, range(14, 20, 14, 34),
        List.of(DSConstructor, DSClassFieldX, DSCLassFieldCls, methodFoo, methodBar));

    final var buzParamA = documentSymbol("a", Variable, range(28, 8, 28, 9));
    final var funcBuz = documentSymbol("buz(Int)", Function, range(28, 4, 28, 7),
        List.of(buzParamA));

    List<DocumentSymbol> answers = List.of(enumLetters, interInterface, annotationClassForTest, DSClass, funcBuz);
    checkDocumentSymbols(answers, virtualFile.findChild("DocumentSymbol.kt"));
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
  private static DocumentSymbol documentSymbol(@NotNull String name,
                                               @NotNull SymbolKind kind,
                                               @NotNull Range range) {
    return documentSymbol(name, kind, range, null);
  }

  @NotNull
  private static DocumentSymbol documentSymbol(@NotNull String name,
                                               @NotNull SymbolKind kind,
                                               @NotNull Range range,
                                               @Nullable List<@NotNull DocumentSymbol> children) {
    return new DocumentSymbol(name, kind, range, range, null, children);
  }

  @SuppressWarnings("SameParameterValue")
  @NotNull
  private static Range range(int line1, int char1, int line2, int char2) {
    return new Range(new Position(line1, char1), new Position(line2, char2));
  }
}
