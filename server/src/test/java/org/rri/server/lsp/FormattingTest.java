package org.rri.server.lsp;

import com.intellij.openapi.util.Ref;
import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.rri.server.LspPath;
import org.rri.server.TestUtil;
import org.rri.server.util.MiscUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class FormattingTest extends LspServerTestBase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    System.setProperty("idea.log.debug.categories", "#org.rri");
  }

  @Override
  protected String getProjectRelativePath() {
    return "formatting/formatting-project";
  }

  @Test
  public void formatting() {
    var lastLineEdit = createTextEdit(3, 2, 3, 2, "  ");
    final Set<? extends TextEdit> expected = new HashSet<>(Set.of(
        createTextEdit(1, 2, 1, 2, "  "),
        createTextEdit(2, 0, 2, 0, "\n\n"),
        lastLineEdit
    ));
    final var filePath = LspPath.fromLocalPath(getProjectPath().resolve("main.py"));
    wholeFileFormatting(expected, filePath);
    expected.remove(lastLineEdit);
    rangeFormatting(expected, filePath);
  }


  private void wholeFileFormatting(@NotNull Set<? extends TextEdit> expected, LspPath filePath) {
    var params = new DocumentFormattingParams();

    params.setTextDocument(TestUtil.getDocumentIdentifier(filePath));
    params.setOptions(defaultOptions());

    checkFormattingResult(expected, server().getTextDocumentService().formatting(params));
  }

  private void rangeFormatting(@NotNull Set<? extends TextEdit> expected, @NotNull LspPath filePath) {
    var params = new DocumentRangeFormattingParams();

    params.setTextDocument(TestUtil.getDocumentIdentifier(filePath));
    params.setOptions(defaultOptions());
    params.setRange(new Range(new Position(0, 0), new Position(2, 0)));

    checkFormattingResult(expected, server().getTextDocumentService().rangeFormatting(params));
  }
  private void checkFormattingResult(@NotNull Set<? extends TextEdit> expected,
                                     CompletableFuture<List<? extends TextEdit>> formattingResultFuture) {
    Ref<List<? extends TextEdit>> formattingResRef = new Ref<>();

    Assertions.assertDoesNotThrow(() -> formattingResRef.set(
        TestUtil.getNonBlockingEdt(formattingResultFuture, 3000)));
    Assertions.assertEquals(expected, new HashSet<>(formattingResRef.get()));
  }

  private TextEdit createTextEdit(int startLine, int startCharacter, int endLine, int endCharacter, String newText) {
    return new TextEdit(new Range(
        new Position(startLine, startCharacter), new Position(endLine, endCharacter)), newText);
  }

  @NotNull
  private FormattingOptions defaultOptions() {
    return MiscUtil.with(
        new FormattingOptions(),
        formattingOptions -> {
          formattingOptions.setInsertSpaces(true);
          formattingOptions.setTabSize(4);
        });
  }
}
