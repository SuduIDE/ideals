package org.rri.server.symbol;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.eclipse.lsp4j.DocumentSymbol;
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
import java.util.ArrayList;
import java.util.List;

import static org.eclipse.lsp4j.SymbolKind.Class;
import static org.eclipse.lsp4j.SymbolKind.Enum;
import static org.eclipse.lsp4j.SymbolKind.*;
import static org.rri.server.TestUtil.newRange;

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

    final var fieldIntX = documentSymbol("x", Field, newRange(5, 14, 5, 15));
    final var fieldClass1Cls = documentSymbol("cls", Constant, newRange(6, 30, 6, 33));

    final var constructorParamX = documentSymbol("x", Variable, newRange(8, 28, 8, 29));
    final var docSymConstructor = documentSymbol("DocumentSymbol(int)", Constructor, newRange(8, 9, 8, 23),
        arrayList(constructorParamX));

    final var paramFooX = documentSymbol("x", Variable, newRange(13, 21, 13, 22));
    final var paramFooStr = documentSymbol("str", Variable, newRange(13, 31, 13, 34));

    final var varFooA = documentSymbol("a", Variable, newRange(14, 8, 14, 9));

    final var varFooProject = documentSymbol("project", Variable, newRange(15, 11, 15, 18));

    final var classFooCls = documentSymbol("cls", Variable, newRange(16, 10, 16, 13));

    final var varFooB = documentSymbol("b", Variable, newRange(17, 12, 17, 13));

    final var methodFoo = documentSymbol("foo(int, String)", Method, newRange(13, 13, 13, 16),
        arrayList(paramFooX, paramFooStr, varFooA, varFooProject, classFooCls, varFooB));

    final var interMethodFoo = documentSymbol("foo()", Method, newRange(22, 9, 22, 12));
    final var entryInterface = documentSymbol("EntryInter", Interface, newRange(21, 19, 21, 29),
        arrayList(interMethodFoo));

    final var enumMemberA = documentSymbol("A", EnumMember, newRange(26, 4, 26, 5));
    final var enumMemberB = documentSymbol("B", EnumMember, newRange(26, 7, 26, 8));
    final var enumLetter = documentSymbol("Letter", Enum, newRange(25, 14, 25, 20),
        arrayList(enumMemberA, enumMemberB));

    final var docSymClass = documentSymbol("DocumentSymbol", Class, newRange(4, 13, 4, 27),
        arrayList(fieldIntX, fieldClass1Cls, docSymConstructor, methodFoo, entryInterface, enumLetter));

    final var answers = arrayList(docSymClass);
    checkDocumentSymbols(answers, virtualFile.findChild("DocumentSymbol.java"));
  }

  @Test
  public void testDocumentSymbolPython() {
    final var virtualFile = myFixture.copyDirectoryToProject("python/project1", "");

    final var asCls2 = documentSymbol("cls2", Variable, newRange(1, 17, 1, 21));

    final var varStringP = documentSymbol("p", Variable, newRange(4, 0, 4, 1));

    final var barBoolB = documentSymbol("b", Variable, newRange(5, 0, 5, 1));

    final var docSymVarX = documentSymbol("x", Field, newRange(11, 13, 11, 14));
    final var docSymVar__const = documentSymbol("__CONST", Constant, newRange(12, 13, 12, 20));
    final var docSymConstructor = documentSymbol("__init__(self)", Constructor, newRange(10, 8, 10, 16),
        arrayList(docSymVarX, docSymVar__const));

    final var fooParamX = documentSymbol("x", Variable, newRange(14, 18, 14, 19));
    final var fooParamY = documentSymbol("y", Variable, newRange(14, 21, 14, 22));
    final var methodFoo = documentSymbol("foo(self, x, y)", Method, newRange(14, 8, 14, 11),
        arrayList(fooParamX, fooParamY));

    final var barParamArgs = documentSymbol("*args", Variable, newRange(17, 19, 17, 23));
    final var barParamKwargs = documentSymbol("**kwargs", Variable, newRange(17, 27, 17, 33));
    final var methodBar = documentSymbol("bar(self, *args, **kwargs)", Method, newRange(17, 8, 17, 11),
        arrayList(barParamArgs, barParamKwargs));

    final var docSymClass = documentSymbol("Document_symbol", Class, newRange(9, 6, 9, 21),
        arrayList(docSymConstructor, methodFoo, methodBar));

    final var varFooBarX = documentSymbol("x", Variable, newRange(22, 12, 22, 13));
    final var varFooBarY = documentSymbol("y", Variable, newRange(22, 18, 22, 19));
    final var varFooBarZ = documentSymbol("z", Variable, newRange(22, 24, 22, 25));
    final var functionFooBar = documentSymbol("foo_bar(x, /, y, *, z)", Function, newRange(22, 4, 22, 11),
        arrayList(varFooBarX, varFooBarY, varFooBarZ));

    final var answers = arrayList(asCls2, varStringP, barBoolB, docSymClass, functionFooBar);
    checkDocumentSymbols(answers, virtualFile.findChild("documentSymbol.py"));
  }

  @Test
  public void testDocumentSymbolKotlin() {
    var virtualFile = myFixture.copyDirectoryToProject("kotlin/project1/src", "");
    virtualFile = virtualFile.findChild("org");
    assertNotNull(virtualFile);

    final var enumMemberA = documentSymbol("A", EnumMember, newRange(5, 2, 5, 3));
    final var enumMemberB = documentSymbol("B", EnumMember, newRange(5, 5, 5, 6));
    final var enumLetters = documentSymbol("Letter", Enum, newRange(4, 11, 4, 17),
        arrayList(enumMemberA, enumMemberB));

    final var interFooParamX = documentSymbol("x", Variable, newRange(9, 10, 9, 11));
    final var interFooParamStr = documentSymbol("str", Variable, newRange(9, 18, 9, 21));
    final var interMethodFoo = documentSymbol("foo(Int, String)", Method, newRange(9, 6, 9, 9),
        arrayList(interFooParamX, interFooParamStr));
    final var interInterface = documentSymbol("Interface", Interface, newRange(8, 10, 8, 19),
        arrayList(interMethodFoo));

    final var annotationClassForTest = documentSymbol("ForTest", Class, newRange(12, 17, 12, 24));

    final var constructorParamX = documentSymbol("x", Variable, newRange(14, 35, 14, 36));
    final var docSymConstructor = documentSymbol("DocumentSymbol(Int)", Constructor, newRange(14, 34, 14, 42),
        arrayList(constructorParamX));
    final var docSymClassFieldX = documentSymbol("x", Field, newRange(15, 14, 15, 15));
    final var docSymCLassFieldCls = documentSymbol("cls", Field, newRange(16, 14, 16, 17));

    final var fooParamX = documentSymbol("x", Variable, newRange(18, 19, 18, 20));
    final var fooParamStr = documentSymbol("str", Variable, newRange(18, 27, 18, 30));

    final var fooVarA = documentSymbol("a", Variable, newRange(19, 8, 19, 9));

    final var fooVarCls = documentSymbol("cls", Variable, newRange(20, 8, 20, 11));

    final var fooVarB = documentSymbol("b", Variable, newRange(21, 8, 21, 9));

    final var methodFoo = documentSymbol("foo(Int, String)", Method, newRange(18, 15, 18, 18),
        arrayList(fooParamX, fooParamStr, fooVarA, fooVarCls, fooVarB));

    final var methodBar = documentSymbol("bar()", Method, newRange(25, 6, 25, 9));

    final var docSymClass = documentSymbol("DocumentSymbol", Class, newRange(14, 20, 14, 34),
        arrayList(docSymConstructor, docSymClassFieldX, docSymCLassFieldCls, methodFoo, methodBar));

    final var buzParamA = documentSymbol("a", Variable, newRange(28, 8, 28, 9));
    final var funcBuz = documentSymbol("buz(Int)", Function, newRange(28, 4, 28, 7),
        arrayList(buzParamA));

    List<DocumentSymbol> answers = arrayList(enumLetters, interInterface, annotationClassForTest, docSymClass, funcBuz);
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
  private static ArrayList<DocumentSymbol> arrayList(DocumentSymbol... symbols) {
    return new ArrayList<>(List.of(symbols));
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
}
