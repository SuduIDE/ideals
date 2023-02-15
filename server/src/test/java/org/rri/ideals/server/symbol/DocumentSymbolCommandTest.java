package org.rri.ideals.server.symbol;

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
import org.rri.ideals.server.LspPath;

import java.lang.String;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.eclipse.lsp4j.SymbolKind.Class;
import static org.eclipse.lsp4j.SymbolKind.Object;
import static org.eclipse.lsp4j.SymbolKind.*;
import static org.rri.ideals.server.TestUtil.newRange;

@RunWith(JUnit4.class)
public class DocumentSymbolCommandTest extends BasePlatformTestCase {
  @Override
  protected boolean isIconRequired() {
    return true;
  }

  @Override
  protected String getTestDataPath() {
    return Paths.get("test-data/symbol").toAbsolutePath().toString();
  }

  @Test
  public void testDocumentSymbolJava() {
    var virtualFile = myFixture.copyDirectoryToProject("java/project1/src", "");
    virtualFile = virtualFile.findChild("org");
    assertNotNull(virtualFile);

    final var fieldIntX = documentSymbol("x: int", Field,
        newRange(5, 2, 5, 16),
        newRange(5, 14, 5, 14));

    final var fieldClass1Cls = documentSymbol("cls: Class1 = new Class1()", Field,
        newRange(6, 2, 6, 49),
        newRange(6, 30, 6, 30));

    final var docSymConstructor = documentSymbol("DocumentSymbol(int)", Method,
        newRange(8, 2, 10, 3),
        newRange(8, 9, 8, 9));

    final var methodFoo = documentSymbol("foo(int, String): int", Method,
        newRange(12, 2, 19, 3),
        newRange(13, 13, 13, 13));

    final var interMethodFoo = documentSymbol("foo(): void", Method,
        newRange(22, 4, 22, 15),
        newRange(22, 9, 22, 9));

    final var entryInterface = documentSymbol("EntryInter", Object,
        newRange(21, 2, 23, 3),
        newRange(21, 19, 21, 19),
        arrayList(interMethodFoo));

    final var enumMemberA = documentSymbol("A: Letter", Field,
        newRange(26, 4, 26, 5),
        newRange(26, 4, 26, 4));

    final var enumMemberB = documentSymbol("B: Letter", Field,
        newRange(26, 7, 26, 8),
        newRange(26, 7, 26, 7));

    final var enumLetter = documentSymbol("Letter", Object,
        newRange(25, 2, 27, 3),
        newRange(25, 14, 25, 14),
        arrayList(enumMemberA, enumMemberB));

    final var docSymClass = documentSymbol("DocumentSymbol", Object, newRange(4, 0, 28, 1),
        newRange(4, 13, 4, 13),
        arrayList(fieldIntX, fieldClass1Cls, docSymConstructor, methodFoo, entryInterface, enumLetter));
    final var docSymFile = documentSymbol("DocumentSymbol.java", Object, newRange(0, 0, 28, 1),
        newRange(0, 0, 0, 0), arrayList(docSymClass));

    final var answers = arrayList(docSymFile);
    checkDocumentSymbols(answers, virtualFile.findChild("DocumentSymbol.java"));
  }

  @Test
  public void testDocumentSymbolPython() {
    final var virtualFile = myFixture.copyDirectoryToProject("python/project1", "");

    final var varStringP = documentSymbol("p", Variable,
        newRange(4, 0, 4, 1),
        newRange(4, 0, 4, 0));

    final var barBoolB = documentSymbol("b", Variable,
        newRange(5, 0, 5, 1),
        newRange(5, 0, 5, 0));

    final var docSymConstructor = documentSymbol("__init__(self)", Method,
        newRange(10, 4, 12, 27),
        newRange(10, 8, 10, 8));

    final var methodFoo = documentSymbol("foo(self, x, y)", Method,
        newRange(14, 4, 15, 12),
        newRange(14, 8, 14, 8));

    final var methodBar = documentSymbol("bar(self, *args, **kwargs)", Method,
        newRange(17, 4, 18, 12),
        newRange(17, 8, 17, 8));

    final var docSymVar__const = documentSymbol("__CONST", Field,
        newRange(12, 8, 12, 20),
        newRange(12, 13, 12, 13));
    final var docSymVarX = documentSymbol("x", Field,
        newRange(11,  8, 11, 14),
        newRange(11, 13, 11, 13));

    final var docSymClass = documentSymbol("Document_symbol(cls2.Class2)", Class,
        newRange(9, 0, 18, 12),
        newRange(9, 6, 9, 6),
        arrayList(docSymConstructor, methodFoo, methodBar, docSymVar__const, docSymVarX));

    final var functionFooBar = documentSymbol("foo_bar(x, /, y, *, z)", Function,
        newRange(21, 0, 23, 26),
        newRange(22, 4, 22, 4));
    final var docSymFile = documentSymbol("documentSymbol.py", Object,
        newRange(0, 0, 24, 0),
        newRange(0, 0, 0, 0),
        arrayList(varStringP, barBoolB, docSymClass, functionFooBar));

    final var answers = arrayList(docSymFile);
    checkDocumentSymbols(answers, virtualFile.findChild("documentSymbol.py"));
  }

//  @Test
//  public void testDocumentSymbolKotlin() {
//    var virtualFile = myFixture.copyDirectoryToProject("kotlin/project1/src", "");
//    virtualFile = virtualFile.findChild("org");
//    assertNotNull(virtualFile);
//
//    final var enumMemberA = documentSymbol("A", EnumMember,
//        newRange(5, 2, 5, 3),
//        newRange(5, 2, 5, 3));
//    final var enumMemberB = documentSymbol("B", EnumMember,
//        newRange(5, 5, 5, 6),
//        newRange(5, 5, 5, 6));
//    final var enumLetters = documentSymbol("Letter", Enum, newRange(4, 11, 4, 17),
//        newRange(4, 11, 4, 17),
//        arrayList(enumMemberA, enumMemberB));
//
//    final var interFooParamX = documentSymbol("x", Variable, newRange(9, 10, 9, 11),
//        newRange(9, 10, 9, 11));
//    final var interFooParamStr = documentSymbol("str", Variable,
//        newRange(9, 18, 9, 21),
//        newRange(9, 18, 9, 21));
//    final var interMethodFoo = documentSymbol("foo(Int, String)", Method,
//        newRange(9, 6, 9, 9),
//        newRange(9, 6, 9, 9),
//        arrayList(interFooParamX, interFooParamStr));
//    final var interInterface = documentSymbol("Interface", Interface,
//        newRange(8, 10, 8, 19),
//        newRange(8, 10, 8, 19),
//        arrayList(interMethodFoo));
//
//    final var annotationClassForTest = documentSymbol("ForTest", Class,
//        newRange(12, 17, 12, 24),
//        newRange(12, 17, 12, 24));
//
//    final var constructorParamX = documentSymbol("x", Variable,
//        newRange(14, 35, 14, 36),
//        newRange(14, 35, 14, 36));
//    final var docSymConstructor = documentSymbol("DocumentSymbol(Int)", Constructor,
//        newRange(14, 34, 14, 42),
//        newRange(14, 34, 14, 42),
//        arrayList(constructorParamX));
//    final var docSymClassFieldX = documentSymbol("x", Field,
//        newRange(15, 14, 15, 15),
//        newRange(15, 14, 15, 15));
//    final var docSymCLassFieldCls = documentSymbol("cls", Field,
//        newRange(16, 14, 16, 17),
//        newRange(16, 14, 16, 17));
//
//    final var fooParamX = documentSymbol("x", Variable,
//        newRange(18, 19, 18, 20),
//        newRange(18, 19, 18, 20));
//    final var fooParamStr = documentSymbol("str", Variable,
//        newRange(18, 27, 18, 30),
//        newRange(18, 27, 18, 30));
//
//    final var fooVarA = documentSymbol("a", Variable,
//        newRange(19, 8, 19, 9),
//        newRange(19, 8, 19, 9));
//
//    final var fooVarCls = documentSymbol("cls", Variable,
//        newRange(20, 8, 20, 11),
//        newRange(20, 8, 20, 11));
//
//    final var fooVarB = documentSymbol("b", Variable,
//        newRange(21, 8, 21, 9),
//        newRange(21, 8, 21, 9));
//
//    final var methodFoo = documentSymbol("foo(Int, String)", Method,
//        newRange(18, 15, 18, 18),
//        newRange(18, 15, 18, 18),
//        arrayList(fooParamX, fooParamStr, fooVarA, fooVarCls, fooVarB));
//
//    final var methodBar = documentSymbol("bar()", Method,
//        newRange(25, 6, 25, 9),
//        newRange(25, 6, 25, 9));
//
//    final var docSymClass = documentSymbol("DocumentSymbol", Class,
//        newRange(14, 20, 14, 34),
//        newRange(14, 20, 14, 34),
//        arrayList(docSymConstructor, docSymClassFieldX, docSymCLassFieldCls, methodFoo, methodBar));
//
//    final var buzParamA = documentSymbol("a", Variable,
//        newRange(28, 8, 28, 9),
//        newRange(28, 8, 28, 9));
//    final var funcBuz = documentSymbol("buz(Int)", Function,
//        newRange(28, 4, 28, 7),
//        newRange(28, 4, 28, 7),
//        arrayList(buzParamA));
//
//    List<DocumentSymbol> answers = arrayList(enumLetters, interInterface, annotationClassForTest, docSymClass, funcBuz);
//    checkDocumentSymbols(answers, virtualFile.findChild("DocumentSymbol.kt"));
//  }

  private void checkDocumentSymbols(@NotNull List<@NotNull DocumentSymbol> answers, @Nullable VirtualFile virtualFile) {
    assertNotNull(virtualFile);
    var service = getProject().getService(DocumentSymbolService.class);
    var actual = service.computeDocumentSymbols(LspPath.fromVirtualFile(virtualFile), () -> {
    }).stream().map(Either::getRight).toList();
    assertEquals(answers, actual);
  }

  @NotNull
  private static ArrayList<DocumentSymbol> arrayList(DocumentSymbol... symbols) {
    return new ArrayList<>(List.of(symbols));
  }

  @NotNull
  private static DocumentSymbol documentSymbol(@NotNull String name,
                                               @NotNull SymbolKind kind,
                                               @NotNull Range range,
                                               @NotNull Range selectionRange) {
    return documentSymbol(name, kind, range, selectionRange, arrayList());
  }

  @NotNull
  private static DocumentSymbol documentSymbol(@NotNull String name,
                                               @NotNull SymbolKind kind,
                                               @NotNull Range range,
                                               @NotNull Range selectionRange,
                                               @Nullable List<@NotNull DocumentSymbol> children) {
    return new DocumentSymbol(name, kind, range, selectionRange, null, children);
  }
}
