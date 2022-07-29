package org.rri.server.lsp;

import com.intellij.openapi.util.Ref;
import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.rri.server.LspPath;
import org.rri.server.TestUtil;
import org.rri.server.formatting.FormattingTestUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class FormattingTest extends LspServerTestBase {
  @Override
  protected String getProjectRelativePath() {
    return "formatting/formatting-project";
  }

  @Test
  public void wholeFileFormatting() {
    final var expected = Set.of(
        TestUtil.createTextEdit(1, 2, 1, 2, "  "),
        TestUtil.createTextEdit(2, 0, 2, 0, "\n\n"),
        TestUtil.createTextEdit(3, 2, 3, 2, "  ")
    );
    final var filePath = LspPath.fromLocalPath(getProjectPath().resolve("main.py"));

    var params = new DocumentFormattingParams();
    params.setTextDocument(TestUtil.getDocumentIdentifier(filePath));
    params.setOptions(FormattingTestUtil.defaultOptions());

    checkFormattingResult(expected, server().getTextDocumentService().formatting(params));
  }

  @Test
  public void rangeFormatting() {
    final var expected = Set.of(
        TestUtil.createTextEdit(1, 2, 1, 2, "  "),
        TestUtil.createTextEdit(2, 0, 2, 0, "\n\n")
    );
    final var filePath = LspPath.fromLocalPath(getProjectPath().resolve("main.py"));

    var params = new DocumentRangeFormattingParams();
    params.setTextDocument(TestUtil.getDocumentIdentifier(filePath));
    params.setOptions(FormattingTestUtil.defaultOptions());
    params.setRange(new Range(new Position(0, 0), new Position(2, 0)));

    checkFormattingResult(expected, server().getTextDocumentService().rangeFormatting(params));
  }

  private void checkFormattingResult(@NotNull Set<? extends TextEdit> expected,
                                     @NotNull CompletableFuture<List<? extends TextEdit>> formattingResultFuture) {
    Ref<List<? extends TextEdit>> formattingResRef = new Ref<>();

    Assertions.assertDoesNotThrow(() -> formattingResRef.set(
        TestUtil.getNonBlockingEdt(formattingResultFuture, 3000)));
    Assertions.assertEquals(expected, new HashSet<>(formattingResRef.get()));
  }
}
