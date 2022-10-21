package org.rri.ideals.server.references.generators;

import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TypeDefinitionParams;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.engine.TestEngine;

import java.util.List;

public class TypeDefinitionTestGenerator extends ReferencesTestGeneratorBase<TypeDefinitionTestGenerator.TypeDefinitionTest> {
  public static class TypeDefinitionTest extends ReferencesTestGeneratorBase.ReferencesTestBase {
    private final TypeDefinitionParams params;

    private TypeDefinitionTest(TypeDefinitionParams params, List<? extends LocationLink> answer) {
      super(answer);
      this.params = params;
    }

    @Override
    public @NotNull TypeDefinitionParams params() {
      return params;
    }
  }

  public TypeDefinitionTestGenerator(@NotNull TestEngine engine,
                                     @NotNull OffsetPositionConverter converter) {
    super(engine, converter);
  }

  protected @NotNull TypeDefinitionTest createReferencesTest(@NotNull String uri, @NotNull Position pos, @NotNull List<? extends LocationLink> locLinks) {
    return new TypeDefinitionTest(new TypeDefinitionParams(new TextDocumentIdentifier(uri), pos), locLinks);
  }
}
