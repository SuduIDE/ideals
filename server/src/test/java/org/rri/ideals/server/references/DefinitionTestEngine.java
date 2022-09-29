package org.rri.ideals.server.references;

import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.rri.ideals.server.TestEngine;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class DefinitionTestEngine extends TestEngine {
  public static class DefinitionTest implements Test {
    private final DefinitionParams params;
    private final Either<List<? extends Location>, List<? extends LocationLink>> answer;

    private DefinitionTest(DefinitionParams params, Either<List<? extends Location>, List<? extends LocationLink>> answer) {
      this.params = params;
      this.answer = answer;
    }

    public DefinitionParams getParams() {
      return params;
    }

    public Either<List<? extends Location>, List<? extends LocationLink>> getAnswer() {
      return answer;
    }
  }

  private static class DefinitionMarker extends Marker {
    private final boolean isTarget;
    private final String id;
    private final boolean isStart;

    private DefinitionMarker(boolean isTarget, String id, Position pos, boolean isStart) {
      this.isTarget = isTarget;
      this.id = id;
      this.isStart = isStart;
    }

    public boolean isTarget() {
      return isTarget;
    }

    public String getId() {
      return id;
    }

    public boolean isStart() {
      return isStart;
    }
  }

  public DefinitionTestEngine(Path directoryPath) {
    super(directoryPath);
  }

  @Override
  protected List<? extends Test> processTokens(Map<String, List<? extends Marker>> markersByDocument) {
    return null;
  }

  @Override
  protected Marker parseSingeMarker(String text) {
    return null;
  }
}
