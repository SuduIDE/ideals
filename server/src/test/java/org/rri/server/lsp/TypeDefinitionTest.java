package org.rri.server.lsp;

import org.eclipse.lsp4j.*;
import org.junit.Test;
import org.rri.server.LspPath;
import org.rri.server.TestUtil;

public class TypeDefinitionTest extends LspServerTestBase {
  @Override
  protected String getProjectRelativePath() {
    return "references/java/project1";
  }

  @Test
  public void typeDefinition() {
    final var filePath = LspPath.fromLocalPath(getProjectPath().resolve("src/TypeDefinitionIntegratingTest.java"));
    final var typeDefinitionParams = new TypeDefinitionParams(new TextDocumentIdentifier(filePath.toLspUri()), new Position(5, 16));
    final var future = server().getTextDocumentService().typeDefinition(typeDefinitionParams);
    final var result = TestUtil.getNonBlockingEdt(future, 30000);

    final var targetRange = new Range(new Position(2, 13), new Position(2, 20));
    final var originalRange = new Range(new Position(5, 16), new Position(5, 17));

    final var anotherFilePath = LspPath.fromLocalPath(getProjectPath().resolve("src/org/Another.java"));
    assertEquals(1, result.getRight().size());
    assertEquals(new LocationLink(anotherFilePath.toLspUri(), targetRange, targetRange, originalRange), result.getRight().get(0));
  }
}