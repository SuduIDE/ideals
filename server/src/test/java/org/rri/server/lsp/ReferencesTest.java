package org.rri.server.lsp;

import org.eclipse.lsp4j.*;
import org.junit.Test;
import org.rri.server.LspPath;
import org.rri.server.TestUtil;

public class ReferencesTest extends LspServerTestBase {
  @Override
  protected String getProjectRelativePath() {
    return "references/java/project1";
  }

  @Test
  public void definition() {
    final var filePath = LspPath.fromLocalPath(getProjectPath().resolve("src/DefinitionIntegratingTest.java"));
    final var definitionParams = new DefinitionParams(new TextDocumentIdentifier(filePath.toLspUri()), new Position(3, 4));
    final var future = server().getTextDocumentService().definition(definitionParams);
    final var result = TestUtil.getNonBlockingEdt(future, 30000);

    final var targetRange = new Range(new Position(2, 8), new Position(2, 9));
    final var originalRange = new Range(new Position(3, 4), new Position(3, 5));

    assertEquals(1, result.getRight().size());
    assertEquals(new LocationLink(filePath.toLspUri(), targetRange, targetRange, originalRange), result.getRight().get(0));
  }

  @Test
  public void typeDefinition() {
    final var filePath = LspPath.fromLocalPath(getProjectPath().resolve("src/TypeDefinitionIntegratingTest.java"));
    final var typeDefinitionParams = new TypeDefinitionParams(new TextDocumentIdentifier(filePath.toLspUri()), new Position(11, 16));
    final var future = server().getTextDocumentService().typeDefinition(typeDefinitionParams);
    final var result = TestUtil.getNonBlockingEdt(future, 30000);

    final var targetRange = new Range(new Position(1, 16), new Position(1, 23));
    final var originalRange = new Range(new Position(11, 16), new Position(11, 17));

    assertEquals(1, result.getRight().size());
    assertEquals(new LocationLink(filePath.toLspUri(), targetRange, targetRange, originalRange), result.getRight().get(0));
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
