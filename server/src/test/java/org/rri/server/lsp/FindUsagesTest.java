package org.rri.server.lsp;

import org.eclipse.lsp4j.*;
import org.junit.Test;
import org.rri.server.LspPath;
import org.rri.server.TestUtil;

public class FindUsagesTest extends LspServerTestBase {
  @Override
  protected String getProjectRelativePath() {
    return "references/java/project1";
  }


  @Test
  public void findUsages() {
    final var filePath = LspPath.fromLocalPath(getProjectPath().resolve("src/FindUsagesIntegratingTest.java"));
    final var findUsagesParams = new ReferenceParams(new TextDocumentIdentifier(filePath.toLspUri()), new Position(3, 4), new ReferenceContext());
    final var future = server().getTextDocumentService().references(findUsagesParams);
    final var result = TestUtil.getNonBlockingEdt(future, 30000);

    final var targetRange = new Range(new Position(3, 4), new Position(3, 5));

    assertEquals(1, result.size());
    assertEquals(new Location(filePath.toLspUri(), targetRange), result.get(0));
  }
}
