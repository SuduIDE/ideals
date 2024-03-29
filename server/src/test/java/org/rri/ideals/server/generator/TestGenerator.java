package org.rri.ideals.server.generator;

import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.ideals.server.engine.TestEngine;

import java.util.List;
import java.util.Map;

abstract public class TestGenerator<T extends TestGenerator.Test> {
  @NotNull
  protected final Map<String, String> textsByFile; // <Path, Text>
  @NotNull
  protected final Map<String, List<TestEngine.Marker>> markersByFile; // <Path, List<Marker>>

  @NotNull
  protected final Map<String, String> textByIgnoredFile;
  @NotNull
  protected final OffsetPositionConverter converter;

  protected TestGenerator(
          @NotNull TestEngine engine,
          @NotNull OffsetPositionConverter converter){
    this.textsByFile = engine.getTextsByFile();
    this.markersByFile = engine.getMarkersByFile();
    this.textByIgnoredFile = engine.getTextByIgnoredFile();
    this.converter = converter;
  }

  abstract public @NotNull List<? extends T> generateTests();

  public interface Test {
    @NotNull Object params();
    @Nullable Object expected();
  }

  public interface OffsetPositionConverter {
    @NotNull Position offsetToPosition(int offset, @NotNull String path);
  }
}
