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
import java.util.ArrayList;
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

    final var packageOrg = documentSymbol("org", Package, range(0, 0, 0 , 12));

    final var importComClass1 = documentSymbol("com.Class1", Module, range(2, 0, 2, 18));

    final var fieldIntX = documentSymbol("x", Field, range(5, 14, 5, 15));
    final var fieldClass1Cls = documentSymbol("cls", Constant, range(6, 30, 6, 33));

    final var constructorParamX = documentSymbol("x", Variable, range(8, 28, 8, 29));
    final var DSConstructor = documentSymbol("DocumentSymbol(int)", Constructor, range(8, 9, 8, 23),
        arrayList(constructorParamX));

    final var overrideFoo = documentSymbol("@Override", Property, range(12, 2, 12, 11));
    final var paramFooX = documentSymbol("x", Variable, range(13, 21, 13, 22));
    final var paramFooStr = documentSymbol("str", Variable, range(13, 31, 13, 34));

    final var literFoo1 = documentSymbol("1", Number, range(14, 12, 14, 13));
    final var varFooA = documentSymbol("a", Variable, range(14, 8, 14, 9),
        arrayList(literFoo1));

    final var literFooStringLsp = documentSymbol("\"lsp\"", String, range(15, 21, 15, 26));
    final var varFooProject = documentSymbol("project", Variable, range(15, 11, 15, 18),
        arrayList(literFooStringLsp));

    final var literFooNull = documentSymbol("null", Null, range(16, 16, 16, 20));
    final var classFooCls = documentSymbol("cls", Variable, range(16, 10, 16, 13),
        arrayList(literFooNull));

    final var literFooTrue = documentSymbol("true", Boolean, range(17, 16, 17, 20));
    final var varFooB = documentSymbol("b", Variable, range(17, 12, 17, 13),
        arrayList(literFooTrue));

    final var methodFoo = documentSymbol("foo(int, String)", Method, range(13, 13, 13, 16),
        arrayList(overrideFoo, paramFooX, paramFooStr, varFooA, varFooProject, classFooCls, varFooB));

    final var interMethodFoo = documentSymbol("foo()", Method, range(22, 9, 22, 12));
    final var entryInterface = documentSymbol("EntryInter", Interface, range(21, 19, 21, 29),
        arrayList(interMethodFoo));

    final var enumMemberA = documentSymbol("A", EnumMember, range(26, 4, 26, 5));
    final var enumMemberB = documentSymbol("B", EnumMember, range(26, 7, 26, 8));
    final var enumLetter = documentSymbol("Letter", Enum, range(25, 14, 25, 20),
        arrayList(enumMemberA, enumMemberB));

    final var DSClass = documentSymbol("DocumentSymbol", Class, range(4, 13, 4, 27),
        arrayList(fieldIntX, fieldClass1Cls, DSConstructor, methodFoo, entryInterface, enumLetter));

    final var DSFile = documentSymbol("DocumentSymbol.java", File, range(0, 0, 28, 1),
        arrayList(packageOrg, importComClass1, DSClass));

    final var answers = List.of(DSFile);
    checkDocumentSymbols(answers, virtualFile.findChild("DocumentSymbol.java"));
  }

  @Test
  public void testDocumentSymbolPython() {
    final var virtualFile = myFixture.copyDirectoryToProject("python/project1", "");

    final var importClass1 = documentSymbol("import class1", Module, range(0, 0, 0, 13));
    final var asCls2 = documentSymbol("cls2", Variable, range(1, 17, 1, 21));
    final var importClass2 = documentSymbol("import class2", Module, range(1, 0, 1, 21),
        arrayList(asCls2));
    final var fromFuncsImportStar = documentSymbol("from funcs import *", Module, range(2, 0, 2, 19));

    final var varStringP = documentSymbol("p", Variable, range(4, 0, 4, 1));
    final var literString = documentSymbol("\"lsp\"", String, range(4, 4, 4, 9));

    final var barBoolB = documentSymbol("b", Variable, range(5, 0, 5, 1));
    final var literTrue = documentSymbol("True", Boolean, range(5, 4, 5, 8));

    final var SelfConstructorParam = documentSymbol("self", Field, range(10, 17, 10, 21));
    final var DSVarX = documentSymbol("x", Field, range(11, 13, 11, 14));
    final var liter1 = documentSymbol("1", Number, range(11, 17, 11, 18));
    final var DSVar__const = documentSymbol("__const", Constant, range(12, 13, 12, 20));
    final var literNone = documentSymbol("None", Null, range(12, 23, 12, 27));
    final var DSConstructor = documentSymbol("__init__(self)", Constructor, range(10, 8, 10, 16),
        arrayList(SelfConstructorParam, DSVarX, liter1, DSVar__const, literNone));

    final var fooParamSelf = documentSymbol("self", Field, range(14, 12, 14, 16));
    final var fooParamX = documentSymbol("x", Variable, range(14, 18, 14, 19));
    final var fooParamY = documentSymbol("y", Variable, range(14, 21, 14, 22));
    final var methodFoo = documentSymbol("foo(self, x, y)", Method, range(14, 8, 14, 11),
        arrayList(fooParamSelf, fooParamX, fooParamY));

    final var barParamSelf = documentSymbol("self", Field, range(17, 12, 17, 16));
    final var barParamArgs = documentSymbol("*args", Variable, range(17, 19, 17, 23));
    final var barParamKwargs = documentSymbol("**kwargs", Variable, range(17, 27, 17, 33));
    final var methodBar = documentSymbol("bar(self, *args, **kwargs)", Method, range(17, 8, 17, 11),
        arrayList(barParamSelf, barParamArgs, barParamKwargs));

    final var DSClass = documentSymbol("Document_symbol", Class, range(9, 6, 9, 21),
        arrayList(DSConstructor, methodFoo, methodBar));

    final var decoratorDoTwice = documentSymbol("@do_twice", Property, range(21, 0, 21, 9));
    final var varFooBarX = documentSymbol("x", Variable, range(22, 12, 22, 13));
    final var varFooBarY = documentSymbol("y", Variable, range(22, 18, 22, 19));
    final var varFooBarZ = documentSymbol("z", Variable, range(22, 24, 22, 25));
    final var functionFooBar = documentSymbol("foo_bar(x, /, y, *, z)", Function, range(22, 4, 22, 11),
        arrayList(decoratorDoTwice, varFooBarX, varFooBarY, varFooBarZ));

    final var DSFIle = documentSymbol("documentSymbol.py", File, range(0, 0, 24, 0),
        arrayList(importClass1, importClass2, fromFuncsImportStar,
            varStringP, literString, barBoolB, literTrue,
            DSClass, functionFooBar));

    final var answers = List.of(DSFIle);
    checkDocumentSymbols(answers, virtualFile.findChild("documentSymbol.py"));
  }

  @Test
  public void testDocumentSymbolKotlin() {
    var virtualFile = myFixture.copyDirectoryToProject("kotlin/project1/src", "");
    virtualFile = virtualFile.findChild("org");
    assertNotNull(virtualFile);

    final var packageOrg = documentSymbol("org", Package, range(0, 0, 0, 11));

    final var importComClass1 = documentSymbol("com.Class1", Module, range(2, 0, 2, 17));

    final var enumMemberA = documentSymbol("A", Class, range(5, 2, 5, 3)); // TODO: Fix it
    final var enumMemberB = documentSymbol("B", Class, range(5, 5, 5, 6)); // TODO: Fix it
    final var enumLetters = documentSymbol("Letter", Enum, range(4, 11, 4, 17),
        arrayList(enumMemberA, enumMemberB));

    final var interFooParamX = documentSymbol("x", Variable, range(9, 10, 9, 11));
    final var interFooParamStr = documentSymbol("str", Variable, range(9, 18, 9, 21));
    final var interMethodFoo = documentSymbol("foo(Int, String)", Method, range(9, 6, 9, 9),
        arrayList(interFooParamX, interFooParamStr));
    final var interInterface = documentSymbol("Interface", Interface, range(8, 10, 8, 19),
        arrayList(interMethodFoo));

    final var annotationParam = documentSymbol("\"for test\"", String, range(12, 9, 12, 19));
    final var annotationSpecial = documentSymbol("<unknown>", Property, range(12, 0, 12, 20),
        arrayList(annotationParam)); // TODO: Fix it

    final var constructorParamX = documentSymbol("x", Variable, range(13, 26, 13, 27));
    final var DSConstructor = documentSymbol("DocumentSymbol(Int)", Constructor, range(13, 25, 13, 33),
        arrayList(constructorParamX));

    final var DSClassFieldX = documentSymbol("x", Field, range(14, 14, 14, 15));
    final var DSCLassFieldCls = documentSymbol("cls", Field, range(15, 14, 15, 17));

    final var fooParamX = documentSymbol("x", Variable, range(17, 19, 17, 20));
    final var fooParamStr = documentSymbol("str", Variable, range(17, 27, 17, 30));

    final var aLiter1 = documentSymbol("1", Number, range(18, 12, 18, 13));
    final var fooVarA = documentSymbol("a", Variable, range(18, 8, 18, 9),
        arrayList(aLiter1));

    final var clsLiterNull = documentSymbol("null", Constant, range(19, 14, 19, 18));
    final var fooVarCls = documentSymbol("cls", Variable, range(19, 8, 19, 11),
        arrayList(clsLiterNull));

    final var bLiterTrue = documentSymbol("true", Boolean, range(20, 12, 20, 16));
    final var fooVarB = documentSymbol("b", Variable, range(20, 8, 20, 9),
        arrayList(bLiterTrue));

    final var methodFoo = documentSymbol("foo(Int, String)", Method, range(17, 15, 17, 18),
        arrayList(fooParamX, fooParamStr, fooVarA, fooVarCls, fooVarB));

    final var barLiter42 = documentSymbol("42", Number, range(24, 19, 24, 21));
    final var methodBar = documentSymbol("bar()", Method, range(24, 6, 24, 9),
        arrayList(barLiter42));

    final var DSClass = documentSymbol("DocumentSymbol", Class, range(13, 11, 13, 25),
        arrayList(annotationSpecial, DSConstructor, DSClassFieldX, DSCLassFieldCls, methodFoo, methodBar));

    final var buzParamA = documentSymbol("a", Variable, range(27, 8, 27, 9));
    final var buzLiter1 = documentSymbol("1", Number, range(27, 27, 27, 28));
    final var funcBuz = documentSymbol("buz(Int)", Function, range(27, 4, 27, 7),
        arrayList(buzParamA, buzLiter1));

    final var DSFile = documentSymbol("DocumentSymbol.kt", File, range(0, 0, 27, 28),
        arrayList(packageOrg, importComClass1, enumLetters, interInterface, DSClass, funcBuz));

    List<DocumentSymbol> answers = List.of(DSFile);
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

  @SuppressWarnings("SameParameterValue")
  @NotNull
  private static Range range(int line1, int char1, int line2, int char2) {
    return new Range(new Position(line1, char1), new Position(line2, char2));
  }
}
