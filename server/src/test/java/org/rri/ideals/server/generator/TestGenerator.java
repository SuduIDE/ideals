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
  protected final OffsetPositionConverter converter;

  protected TestGenerator(
          @NotNull Map<@NotNull String, @NotNull String> textsByFile,
          @NotNull Map<@NotNull String, @NotNull List<TestEngine.Marker>> markersByFile,
          @NotNull OffsetPositionConverter converter){
    this.textsByFile = textsByFile;
    this.markersByFile = markersByFile;
    this.converter = converter;
  }

  abstract public @NotNull List<? extends T> generateTests();

  public interface Test {
    @NotNull Object params();
    @Nullable Object answer();
  }

  public interface OffsetPositionConverter {
    @NotNull Position offsetToPosition(int offset, @NotNull String path);
  }
}
