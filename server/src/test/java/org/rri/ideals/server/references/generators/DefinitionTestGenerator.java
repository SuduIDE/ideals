package org.rri.ideals.server.references.generators;

import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.engine.TestEngine;

import java.util.Set;

public class DefinitionTestGenerator extends ReferencesTestGeneratorBase<DefinitionTestGenerator.DefinitionTest> {
  public static class DefinitionTest extends ReferencesTestGeneratorBase.ReferencesTestBase {
    @NotNull
    private final DefinitionParams params;

    private DefinitionTest(@NotNull DefinitionParams params, @NotNull Set<? extends LocationLink> answer) {
      super(answer);
      this.params = params;
    }

    @Override
    public @NotNull DefinitionParams params() {
      return params;
    }
  }

  public DefinitionTestGenerator(@NotNull TestEngine engine,
                                 @NotNull OffsetPositionConverter converter) {
    super(engine, converter);
  }

  protected @NotNull DefinitionTest createReferencesTest(@NotNull String uri, @NotNull Position pos, @NotNull Set<? extends LocationLink> locLinks) {
    return new DefinitionTest(new DefinitionParams(new TextDocumentIdentifier(uri), pos), locLinks);
  }
}
