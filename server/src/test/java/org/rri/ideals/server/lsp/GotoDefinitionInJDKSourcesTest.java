package org.rri.ideals.server.lsp;

import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.Ignore;
import org.junit.Test;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.TestUtil;

import java.util.HashSet;
import java.util.Set;

public class GotoDefinitionInJDKSourcesTest extends LspServerTestBase {
  @Override
  protected String getProjectRelativePath() {
    return "references/java/project-definition-bug";
  }

  @Test
  @Ignore // TODO answer from server always empty
  public void definition() {
    final var filePath = LspPath.fromLocalPath(getProjectPath().resolve("src/DefinitionFromJar.java"));
    final var future =
        server().getTextDocumentService()
            .definition(
                new DefinitionParams(
                    new TextDocumentIdentifier(filePath.toLspUri()), new Position(3, 8)));
    var actual = TestUtil.getNonBlockingEdt(future, 50000);
    assertNotNull(actual);
    assertEquals(Set.of(), new HashSet<>(actual.getRight()));
  }
}
