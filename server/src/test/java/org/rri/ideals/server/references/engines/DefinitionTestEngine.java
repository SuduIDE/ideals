package org.rri.ideals.server.references.engines;

import com.intellij.openapi.project.Project;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.TestLexer;

import java.util.List;
import java.util.Map;

public class DefinitionTestEngine extends ReferencesTestEngineBase<DefinitionTestEngine.DefinitionTest>{
  public static class DefinitionTest extends ReferencesTestEngineBase.ReferencesTestBase {
    @NotNull
    private final DefinitionParams params;

    private DefinitionTest(@NotNull DefinitionParams params, @NotNull List<? extends LocationLink> answer) {
      super(answer);
      this.params = params;
    }

    @Override
    public @NotNull DefinitionParams params() {
      return params;
    }
  }

  public DefinitionTestEngine(@NotNull Project project,
                              @NotNull Map<@NotNull String, @NotNull String> textsByFile,
                              @NotNull Map<@NotNull String, @NotNull List<TestLexer.Marker>> markersByFile) {
    super(project, textsByFile, markersByFile);
  }

  protected @NotNull DefinitionTest createReferencesTest(@NotNull String uri, @NotNull Position pos, @NotNull List<? extends LocationLink> locLinks) {
    return new DefinitionTest(new DefinitionParams(new TextDocumentIdentifier(uri), pos), locLinks);
  }
}
