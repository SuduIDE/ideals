package org.rri.server.lsp;

import com.intellij.openapi.util.Ref;
import org.eclipse.lsp4j.*;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.rri.server.LspPath;
import org.rri.server.TestUtil;
import org.rri.server.util.MiscUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    final Set<? extends TextEdit> expected = Set.of(
        createTextEdit(3, 2, 3, 2, "  "),
        createTextEdit(1, 2, 1, 2, "  "),
        createTextEdit(2, 0, 2, 0, "\n\n")
    );

    final var filePath = LspPath.fromLocalPath(getProjectPath().resolve("main.py"));

    var params = new DocumentFormattingParams();
    params.setTextDocument(
        MiscUtil.with(new TextDocumentIdentifier(),
            documentIdentifier -> documentIdentifier.setUri(filePath.toLspUri())));
    params.setOptions(MiscUtil.with(
        new FormattingOptions(),
        formattingOptions -> {
          formattingOptions.setInsertSpaces(true);
          formattingOptions.setTabSize(4);
        }));

    Ref<List<? extends TextEdit>> formattingResRef = new Ref<>();

    Assertions.assertDoesNotThrow(() -> formattingResRef.set(
        TestUtil.getNonBlockingEdt(server().getTextDocumentService().formatting(params), 3000)));
    Assertions.assertNotNull(formattingResRef.get());
    Assertions.assertEquals(expected, new HashSet<>(formattingResRef.get()));
  }

  private TextEdit createTextEdit(int startLine, int startCharacter, int endLine, int endCharacter, String newText) {
    return new TextEdit(new Range(
        new Position(startLine, startCharacter), new Position(endLine, endCharacter)), newText);
  }
}
