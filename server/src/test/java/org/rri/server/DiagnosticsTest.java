package org.rri.server;

import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.Assert;
import org.junit.Test;
import org.rri.server.util.MiscUtil;

import java.nio.file.Files;

public class DiagnosticsTest extends LspServerTestBase {

  @Override
  protected void setUp() throws Exception {
    System.setProperty("idea.log.debug.categories", "#org.rri");
    super.setUp();
  }

  @Override
  protected String getProjectRelativePath() {
    return "project";
  }

  @Test
  public void didOpen() {
    final var filePath = LspPath.fromLocalPath(getProjectPath().resolve("src/Test.java"));

    //noinspection CodeBlock2Expr
    final var didOpenTextDocumentParams = MiscUtil.with(new DidOpenTextDocumentParams(), params -> {
      params.setTextDocument(MiscUtil.with(new TextDocumentItem(), item -> {
        item.setUri(filePath.toLspUri());

        item.setText(MiscUtil.unexceptionize(() -> Files.readString(filePath.toPath())));
        item.setVersion(1);
      }));
    });

    server().getTextDocumentService().didOpen(didOpenTextDocumentParams);

    final var diagnosticsParams = client().waitAndGetDiagnosticsPublished();

    Assert.assertEquals(filePath, LspPath.fromLspUri(diagnosticsParams.getUri()));
    Assert.assertEquals(1, diagnosticsParams.getDiagnostics().size());


    final var diagnostic = diagnosticsParams.getDiagnostics().get(0);
    Assert.assertEquals("';' expected", diagnostic.getMessage());
    Assert.assertEquals(new Range(new Position(3, 13), new Position(3, 14)), diagnostic.getRange());
  }
}
