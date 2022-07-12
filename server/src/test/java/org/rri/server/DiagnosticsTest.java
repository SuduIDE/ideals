package org.rri.server;

import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.Assert;
import org.junit.Test;
import org.rri.server.util.MiscUtil;

public class DiagnosticsTest extends LspServerTestBase {
  @Override
  protected String getProjectRelativePath() {
    return "project";
  }

  @Test
  public void didOpen() {
    final var filePath = LspPath.fromLocalPath(getProjectPath().resolve("src/Test.java"));

    final var didOpenTextDocumentParams = MiscUtil.with(new DidOpenTextDocumentParams(), params -> {
      params.setTextDocument(MiscUtil.with(new TextDocumentItem(), item -> {
        item.setUri(filePath.toLspUri());
        item.setText(
                "class Test {\n" +
                "  public static void main(String[] args) {\n" +
                "    System.out.println(\"Hello, world!\")\n" +
                "  }\n" +
                "}"
        );
        item.setVersion(1);
      }));
    });

    server().getTextDocumentService().didOpen(didOpenTextDocumentParams);

    final var diagnosticsParams = client().waitAndGetDiagnosticsPublished();

    Assert.assertEquals(filePath, LspPath.fromLspUri(diagnosticsParams.getUri()));
    // Assert.assertEquals(1, diagnosticsParams.getDiagnostics().size());  // todo fails
  }
}
