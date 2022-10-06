package org.rri.ideals.server;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

abstract public class TestEngine<T extends TestEngine.Test, M extends TestEngine.Marker> {
  public interface Test {
    @NotNull Object getParams();
    @Nullable Object getAnswer();
  }

  protected static abstract class Marker {
    private int offset;

    public void setOffset(int offset) {
      this.offset = offset;
    }

    public int getOffset() {
      return offset;
    }
  }

  protected interface InsertTextMarker  {
    @NotNull String getText();
  }

  @NotNull
  private final Path targetDirectory;
  @NotNull
  protected final Map<@NotNull String, @NotNull String> textsByFile; // <Path, Text>
  @NotNull
  protected Map<@NotNull String, @NotNull List<@NotNull M>> markersByFile; // <Path, List<Marker>>
  @NotNull
  protected Project project;

  protected TestEngine(@NotNull Path targetDirectory, @NotNull Project project) throws IOException {
    if(!Files.isDirectory(targetDirectory)) {
      throw new IOException("Path is not a directory");
    }
    this.targetDirectory = targetDirectory;
    this.project = project;
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

  private void preprocessFile(@NotNull Path path) throws RuntimeException {
    if (Files.isDirectory(path)) {
      return;
    }
    try (BufferedReader reader = Files.newBufferedReader(path)) {
      int num;
      int offset = 0;
      final StringBuilder builder = new StringBuilder();
      final StringBuilder markerBuilder = new StringBuilder();
      final List<M> markers = new ArrayList<>();
      while((num = reader.read()) != -1) {
        char c = (char) num;
        if (c == '<') {
          c = (char) reader.read();
          if (c == '/') {
            markerBuilder.setLength(0);
            while((c = (char) reader.read()) != '>') {
              markerBuilder.append(c);
            }
            final var marker = parseSingeMarker(markerBuilder.toString());
            if (marker instanceof final InsertTextMarker insertMarker) {
              builder.append(insertMarker.getText());
            } else {
              marker.setOffset(offset);
              markers.add(marker);
            }
          } else {
            builder.append('<');
            builder.append(c);
            offset += 2;
          }
        } else {
          builder.append(c);
          offset++;
        }
      }
      textsByFile.put(path.toString(), builder.toString());
      markersByFile.put(path.toString(), markers);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void processPath(@NotNull Path path, @NotNull TestFixture fixture) {
    if (!Files.isDirectory(path)) {
      var text = textsByFile.remove(path.toString());
      final var newPath = fixture.writeFileToProject(TestUtil.getPathTail(targetDirectory, path), text);
      final var markers = markersByFile.remove(path.toString());
      markersByFile.put(newPath.toLspUri(), markers);
      textsByFile.put(newPath.toLspUri(), text);
    }
  }

  public List<? extends T> generateTests(@NotNull TestFixture fixture) throws IOException {
    try (final var stream = Files.newDirectoryStream(targetDirectory)) {
      for (final var path : stream) {
        final var name = path.toFile().getName();
        if (name.equals(".idea")) {
          fixture.copyDirectoryToProject(path);
          continue;
        } else if (name.matches(".*\\.iml")) {
          fixture.copyFileToProject(path);
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

  abstract protected @NotNull List<? extends T> processMarkers();

  abstract protected @NotNull M parseSingeMarker(String markerText);
}
