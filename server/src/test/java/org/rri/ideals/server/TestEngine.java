package org.rri.ideals.server;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

abstract public class TestEngine<T extends TestEngine.Test> {
  public interface Test {
    @NotNull Object params();
    @Nullable Object answer();
  }

  @NotNull
  protected final Project project;
  @NotNull
  protected final Map<String, String> textsByFile; // <Path, Text>
  @NotNull
  protected final Map<String, List<TestLexer.Marker>> markersByFile; // <Path, List<Marker>>

  protected TestEngine(@NotNull Project project, @NotNull Map<@NotNull String, @NotNull String> textsByFile, @NotNull Map<@NotNull String, @NotNull List<TestLexer.Marker>> markersByFile){
    this.project = project;
    this.textsByFile = textsByFile;
    this.markersByFile = markersByFile;
  }

  abstract public @NotNull List<? extends T> generateTests();
}
