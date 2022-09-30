package org.rri.ideals.server.completions;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.TestEngine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class CompletionTestEngine extends
    TestEngine<CompletionTestEngine.CompletionTest, CompletionTestEngine.CompletionMarker> {
  public CompletionTestEngine(Path targetDirectory, Project project) throws IOException {
    super(targetDirectory, project);
  }

  @Override
  protected List<? extends CompletionTest> processMarkers() {
    return null;
  }

  @Override
  protected CompletionMarker parseSingeMarker(String markerText) {
    return null;
  }

  public static class CompletionTest implements TestEngine.Test {
    @Override
    @NotNull
    public Object getParams() {
      return null;
    }

    @Override
    @NotNull
    public Object getAnswer() {
      return null;
    }
  }

  protected static class CompletionMarker extends TestEngine.Marker {

  }
}
