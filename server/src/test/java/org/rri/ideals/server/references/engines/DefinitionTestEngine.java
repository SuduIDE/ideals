package org.rri.ideals.server.references.engines;

import com.intellij.openapi.project.Project;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class DefinitionTestEngine extends ReferencesTestEngineBase<DefinitionTestEngine.DefinitionTest>{
  public static class DefinitionTest extends ReferencesTestEngineBase.ReferencesTestBase {
    private final DefinitionParams params;

    private DefinitionTest(DefinitionParams params, List<? extends LocationLink> answer) {
      super(answer);
      this.params = params;
    }

    @Override
    public DefinitionParams getParams() {
      return params;
    }
  }

  public DefinitionTestEngine(Path directoryPath, Project project) throws IOException {
    super(directoryPath, project);
  }
  protected DefinitionTest createReferencesTest(String uri, Position pos, List<LocationLink> locLinks) {
    return new DefinitionTest(new DefinitionParams(new TextDocumentIdentifier(uri), pos), locLinks);
  }
}
