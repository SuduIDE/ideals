package org.rri.ideals.server.engine;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.ideals.server.util.MiscUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;


public class TestEngine {
  @NotNull
  private final TestFixture fixture;

  @NotNull
  private final Map<@NotNull String, @NotNull String> textsByFile; // <Path, Text>

  @NotNull
  private final Map<@NotNull String, @NotNull List<@NotNull Marker>> markersByFile; // <Path, List<Marker>>

  public TestEngine(@NotNull TestFixture fixture) {
    this.fixture = fixture;
    this.textsByFile = new HashMap<>();
    this.markersByFile = new HashMap<>();
  }

  @NotNull
  public Map<String, String> getTextsByFile() {
    return textsByFile;
  }

  @NotNull
  public Map<String, List<Marker>> getMarkersByFile() {
    return markersByFile;
  }

  @NotNull
  private Path getTestDataPath() {
    return fixture.getTestDataPath();
  }

  private void preprocessFiles() {
    try (final var stream = Files.newDirectoryStream(getTestDataPath())) {
      for(final var path : stream) {
        final var name = path.toFile().getName();
        if (name.equals(".idea") || name.contains(".iml")) {
          continue;
        }
        if (Files.isDirectory(path)) {
          try (final var filesStream = Files.walk(path)) {
            filesStream.forEach(MiscUtil.toConsumer(this::preprocessFile));
          }
        } else {
          preprocessFile(path);
        }
      }
    }
    catch (IOException e) {
      throw MiscUtil.wrap(e);
    }
  }

  private void preprocessFile(@NotNull Path path) throws IOException {
    if (Files.isDirectory(path)) {
      return;
    }

    try (BufferedReader reader = Files.newBufferedReader(path)) {
      int num;
      int offset = 0;
      final StringBuilder builder = new StringBuilder();
      final StringBuilder labelBuilder = new StringBuilder();
      final List<Marker> markers = new ArrayList<>();
      final Stack<Label> labels = new Stack<>();
      while((num = reader.read()) != -1) {
        char c = (char) num;
        if (c == '<') {
          c = (char) reader.read();
          if (c == '/') {
            labelBuilder.setLength(0);
            while((c = (char) reader.read()) != '>') {
              labelBuilder.append(c);
            }
            final var label = new Label(labelBuilder.toString(), offset);
            String insertText = getInsertTextFromLabelIfExist(label);
            if (insertText != null) {
              builder.append(insertText);
            } else {
              if (label.isCloseLabel()) {
                var openLabel = labels.pop();
                assert openLabel.name != null;
                var srcText = builder.toString();
                Marker marker = new Marker(
                        openLabel.name,
                        srcText.substring(openLabel.offset, label.offset),
                        new RangeAsOffsets(openLabel.offset, label.offset));
                marker.additionalData.putAll(openLabel.additionalData);
                marker.additionalData.putAll(label.additionalData);
                markers.add(marker);
              } else if (label.isSingleLabel()) {
                assert label.name != null;
                Marker marker = new Marker(label.name, null, new RangeAsOffsets(label.offset, label.offset));
                marker.additionalData.putAll(label.additionalData);
                markers.add(marker);
              } else {
                labels.add(label);
              }
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
    }
  }

  private String getInsertTextFromLabelIfExist(Label label) {
    return switch (label.labelContent) {
      case "s" -> "\s";
      case "t" -> "\t";
      default -> null;
    };
  }

  private void processPath(@NotNull Path path, @NotNull TestFixture fixture) {
    if (!Files.isDirectory(path)) {
      var text = textsByFile.remove(path.toString());
      final var newPath =
          MiscUtil.uncheckExceptions(() -> fixture.writeFileToProject(getTestDataPath().relativize(path).toString(), text));
      final var markers = markersByFile.remove(path.toString());
      markersByFile.put(newPath.toLspUri(), markers);
      textsByFile.put(newPath.toLspUri(), text);
    }
  }

  public void initSandbox(@NotNull String relativePathToTestProject) {
    preprocessFiles();
    try (final var stream = Files.newDirectoryStream(getTestDataPath().resolve(relativePathToTestProject))) {
      for (final var path : stream) {
        final var name = path.toFile().getName();
        if (Objects.equals(name, ".idea")) {
          fixture.copyDirectoryToProject(getTestDataPath().relativize(path));
          continue;
        } else if (name.matches(".*\\.iml")) {
          fixture.copyFileToProject(getTestDataPath().relativize(path));
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
    catch (IOException e) {
      throw MiscUtil.wrap(e);
    }
  }

  public record RangeAsOffsets(int startOffset, int endOffset) {}

  public static class Marker {
    @NotNull public final String name;
    @NotNull public final RangeAsOffsets range;
    @Nullable public final String content;
    @NotNull public final Map<String, String> additionalData = new HashMap<>();

    public Marker(@NotNull String name, @Nullable String content, @NotNull RangeAsOffsets range) {
      this.name = name;
      this.content = content;
      this.range = range;
    }
  }

  private static class Label {
    @NotNull final String labelContent;
    @Nullable final String name;
    final int offset;
    final boolean isSingleLabel;
    @NotNull final Map<String, String> additionalData = new HashMap<>();

    private Label(@NotNull String labelContent, int offset) {
      this.offset = offset;
      if ("".equals(labelContent)) {
        this.name = null;
        this.labelContent = "";
        isSingleLabel = false;
        return;
      }
      if (labelContent.length() > 0 && labelContent.charAt(labelContent.length() - 1) == '/') {
        labelContent = labelContent.substring(0, labelContent.length()-1);
        this.isSingleLabel = true;
      } else {
        this.isSingleLabel = false;
      }
      this.labelContent = labelContent;
      var splitLabelElements = this.labelContent.split("\s+");
      if (splitLabelElements.length == 0) {
        this.name = null;
      } else {
        this.name = splitLabelElements[0];
        var correctSplit = new ArrayList<String>();
        for (String s : splitLabelElements) {
          var splitRes = s.split("=");
          correctSplit.addAll(List.of(splitRes));
        }
        for (int i = 1; i + 1 < correctSplit.size(); i += 2) {
          additionalData.put(correctSplit.get(i), correctSplit.get(i + 1));
        }
      }
    }

    public boolean isSingleLabel() {
      return this.isSingleLabel;
    }

    public boolean isCloseLabel() {
      return name == null;
    }
  }
}