package org.rri.ideals.server;

import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
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
  private final Map<Path, String> textsByFile;
  protected Map<Path, List<? extends Marker>> markersByFile;

  protected TestEngine(Path targetDirectory) throws IOException {
    if(!Files.isDirectory(targetDirectory)) { throw new IOException("Path is not a directory"); }
    this.targetDirectory = targetDirectory;
    textsByFile = new HashMap<>();
    markersByFile = new HashMap<>();
    preprocessFiles();
  }

  private void preprocessFiles() throws IOException {
    try (final var stream = Files.newDirectoryStream(targetDirectory)) {
      for(final var path : stream) {
        final var name = path.toFile().getName();
        if (name.equals(".idea") || name.contains(".iml")) {
          continue;
        }
        if (Files.isDirectory(path)) {
          try (final var filesStream = Files.walk(path)) {
            filesStream.forEach(this::preprocessFile);
          }
        } else {
          preprocessFile(path);
        }
      }
    }
  }

  private void preprocessFile(@NotNull Path path) {
    // preprocess one file: delete markers and update markersByFile
  }

  public List<? extends Test> generateTests(Path sandboxDirectory) {
    // Write files in directory
    return processMarkers();
  }

  private void processPath(@NotNull Path path, @NotNull CodeInsightTestFixture fixture) {
    StringBuilder builder = new StringBuilder();
    for (int i = targetDirectory.getNameCount(); i < path.getNameCount(); i++) {
      builder.append(path.getName(i).toFile().getName());
      if (i != path.getNameCount() - 1) {
        builder.append('/');
      }
    }
    final var newFile = fixture.addFileToProject(builder.toString(), textsByFile.get(path));
    final var newPath = LspPath.fromVirtualFile(newFile.getVirtualFile());
    final var markers = markersByFile.remove(path);
    markersByFile.put(newPath.toPath(), markers);
  }

  public List<? extends Test> generateTests(CodeInsightTestFixture fixture) throws IOException{
    try (final var stream = Files.newDirectoryStream(targetDirectory)) {
      for (final var path : stream) {
        final var name = path.toFile().getName();
        if (name.equals(".idea")) {
          fixture.copyDirectoryToProject(path.toUri().getPath(), "");
          continue;
        } else if (name.matches(".*\\.iml")) {
          fixture.copyFileToProject(path.toUri().getPath());
          continue;
        }
        if (Files.isDirectory(path)) {
          try (final var filesStream = Files.walk(path)) {
            filesStream.forEach(curPath -> processPath(curPath, fixture));
          }
        } else {
          processPath(path, fixture);
        }
      }
    }
    return processMarkers();
  }

  abstract protected List<? extends Test> processMarkers(); // <Document Uri, List<Marker>>

  abstract protected Marker parseSingeMarker(String text);
}
