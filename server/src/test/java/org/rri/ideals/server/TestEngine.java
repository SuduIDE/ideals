package org.rri.ideals.server;

import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.eclipse.lsp4j.Position;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

abstract public class TestEngine {
  public interface Test {
    Object getParams();
    Object getAnswer();
  }

  protected static abstract class Marker {
    private Position position;

    public void setPosition(Position position) {
      this.position = position;
    }

    public Position getPosition() {
      return position;
    }
  }

  private final Path targetDirectory;
  private final List<String> texts;
  private Map<String, List<? extends Marker>> markersByDocument;

  protected TestEngine(Path targetDirectory) {
    this.targetDirectory = targetDirectory;
    texts = new ArrayList<>();
  }

  private void preprocessFiles() {
    // Pass the text and find tokens
  }

  public void writeFilesToSandbox(Path sandboxDirectory) {
    // Write files in directory
  }

  // RETURN virtualFile to fixture directory
  public void copyFilesToFixture(CodeInsightTestFixture fixture) {
    // Write files in fixture
  }

  public List<? extends Test> generateTests() {
    return null;
  }

  abstract protected List<? extends Test> processTokens(Map<String, List<? extends Marker>> markersByDocument); // <Document Uri, List<Marker>>

  abstract protected Marker parseSingeMarker(String text);
}
