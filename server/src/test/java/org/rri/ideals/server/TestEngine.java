package org.rri.ideals.server;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.util.MiscUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
  protected Map<Path, List<Marker>> markersByFile;

  protected TestEngine(Path targetDirectory, Project project) throws IOException {
    if(!Files.isDirectory(targetDirectory)) { throw new IOException("Path is not a directory"); }
    this.targetDirectory = targetDirectory;
    textsByFile = new HashMap<>();
    markersByFile = new HashMap<>();
    preprocessFiles(project);
  }

  private void preprocessFiles(Project project) throws IOException {
    try (final var stream = Files.newDirectoryStream(targetDirectory)) {
      for(final var path : stream) {
        final var name = path.toFile().getName();
        if (name.equals(".idea") || name.contains(".iml")) {
          continue;
        }
        if (Files.isDirectory(path)) {
          try (final var filesStream = Files.walk(path)) {
            filesStream.forEach(curPath -> preprocessFile(curPath, project));
          }
        } else {
          preprocessFile(path, project);
        }
      }
    }
  }

  private void preprocessFile(@NotNull Path path, Project project) throws RuntimeException {
    try (BufferedReader reader = Files.newBufferedReader(path)) {
      final var file = MiscUtil.resolvePsiFile(project, LspPath.fromLocalPath(path));
      if (file == null) {
        throw new RuntimeException("PsiFile is null. Path: " + path);
      }
      final var doc = MiscUtil.getDocument(file);
      if (doc == null) {
        throw new RuntimeException("Document is null. Path: " + path);
      }
      int c;
      int offset = 0;
      final StringBuilder builder = new StringBuilder();
      final StringBuilder markerBuilder = new StringBuilder();
      final List<Marker> markers = new ArrayList<>();
      while((c = reader.read()) != -1) {
        if (c == '<') {
          c = reader.read();
          if (c == '/') {
            markerBuilder.setLength(0);
            while((c = reader.read()) != '>') {
              markerBuilder.append(c);
            }
            final var marker = parseSingeMarker(markerBuilder.toString());
            marker.setPosition(MiscUtil.offsetToPosition(doc, offset));
            markers.add(marker);
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
      textsByFile.put(path, builder.toString());
      markersByFile.put(path, markers);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("unused")
  public List<? extends Test> generateTests(Path sandboxDirectory) {
    // Copy file to the target directory
    return processMarkers();
  }

  private String getPathTail(Path directoryPath, Path path) {
    StringBuilder builder = new StringBuilder();
    for (int i = directoryPath.getNameCount(); i < path.getNameCount(); i++) {
      builder.append(path.getName(i).toFile().getName());
      if (i != path.getNameCount() - 1) {
        builder.append('/');
      }
    }
    return builder.toString();
  }

  private void processPath(@NotNull Path path, @NotNull CodeInsightTestFixture fixture) {
    final var pathTail = getPathTail(targetDirectory, path);
    final var newFile = fixture.addFileToProject(pathTail, textsByFile.get(path));
    final var newPath = LspPath.fromVirtualFile(newFile.getVirtualFile());
    final var markers = markersByFile.remove(path);
    markersByFile.put(newPath.toPath(), markers);
  }

  public List<? extends Test> generateTests(CodeInsightTestFixture fixture) throws IOException {
    try (final var stream = Files.newDirectoryStream(targetDirectory)) {
      for (final var path : stream) {
        final var name = path.toFile().getName();
        final var dirPath = Paths.get(fixture.getTestDataPath());
        if (name.equals(".idea")) {
          fixture.copyDirectoryToProject(getPathTail(dirPath, path), "");
          continue;
        } else if (name.matches(".*\\.iml")) {
          fixture.copyFileToProject(getPathTail(dirPath, path));
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

  abstract protected List<? extends Test> processMarkers();

  abstract protected Marker parseSingeMarker(String markerText);
}
