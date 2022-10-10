package org.rri.ideals.server;


import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;


public class TestLexer {
  public record RangeAsOffsets(int startOffset, int endOffset) {}

  public static class Marker {
    @NotNull final String name;
    @NotNull final RangeAsOffsets range;
    @Nullable final String content;
    @NotNull final Map<String, String> additionalData = new HashMap<>();

    public Marker(@NotNull String name, @Nullable String content, @NotNull RangeAsOffsets range) {
      this.name = name;
      this.content = content;
      this.range = range;
    }
  }

  private static class Token {
    @NotNull final String tokenContent;
    @Nullable final String name;
    final int offset;
    final boolean isSingleToken;
    @NotNull final Map<String, String> additionalData = new HashMap<>();

    private Token(@NotNull String tokenContent, int offset) {
      this.offset = offset;
      if ("".equals(tokenContent)) {
        this.name = null;
        this.tokenContent = "";
        isSingleToken = false;
        return;
      }
      if (tokenContent.length() > 0 && tokenContent.charAt(tokenContent.length() - 1) == '/') {
        tokenContent = tokenContent.substring(0, tokenContent.length()-1);
        this.isSingleToken = true;
      } else {
        this.isSingleToken = false;
      }
      this.tokenContent = tokenContent;
      var splitTokenElements = this.tokenContent.split("\s+");
      if (splitTokenElements.length == 0) {
        this.name = null;
      } else {
        this.name = splitTokenElements[0];
        var correctSplit = new ArrayList<String>();
        for (String s : splitTokenElements) {
          var splitRes = s.split("=");
          correctSplit.addAll(List.of(splitRes));
        }
        for (int i = 1; i + 1 < correctSplit.size(); i += 2) {
          additionalData.put(correctSplit.get(i), correctSplit.get(i + 1));
        }
      }
    }

    public boolean isSingleToken() {
      return this.isSingleToken;
    }

    public boolean isCloseToken() {
      return name == null;
    }
  }

  @NotNull
  private final Path targetDirectory;

  @NotNull
  public final Map<@NotNull String, @NotNull String> textsByFile; // <Path, Text>

  @NotNull
  public Map<@NotNull String, @NotNull List<@NotNull Marker>> markersByFile; // <Path, List<Marker>>

  @NotNull
  protected final Project project;


  public TestLexer(@NotNull Path targetDirectory, @NotNull Project project) throws IOException {
    this.targetDirectory = targetDirectory;
    this.project = project;
    this.textsByFile = new HashMap<>();
    this.markersByFile = new HashMap<>();
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
      final StringBuilder tokenBuilder = new StringBuilder();
      final List<Marker> markers = new ArrayList<>();
      final Stack<Token> tokens = new Stack<>();
      while((num = reader.read()) != -1) {
        char c = (char) num;
        if (c == '<') {
          c = (char) reader.read();
          if (c == '/') {
            tokenBuilder.setLength(0);
            while((c = (char) reader.read()) != '>') {
              tokenBuilder.append(c);
            }
            final var token = new Token(tokenBuilder.toString(), offset);
            String insertText = getInsertTextFromTokenIfExist(token);
            if (insertText != null) {
              builder.append(insertText);
            } else {
              if (token.isCloseToken()) {
                var openToken = tokens.pop();
                assert openToken.name != null;
                var srcText = builder.toString();
                Marker marker = new Marker(
                        openToken.name,
                        srcText.substring(openToken.offset, token.offset),
                        new RangeAsOffsets(openToken.offset, token.offset));
                marker.additionalData.putAll(openToken.additionalData);
                marker.additionalData.putAll(token.additionalData);
                markers.add(marker);
              } else if (token.isSingleToken()) {
                assert token.name != null;
                Marker marker = new Marker(token.name, null, new RangeAsOffsets(token.offset, token.offset));
                marker.additionalData.putAll(token.additionalData);
                markers.add(marker);
              } else {
                tokens.add(token);
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
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private String getInsertTextFromTokenIfExist(Token token) {
    return switch (token.tokenContent) {
      case "s/" -> "\s";
      case "t/" -> "\t";
      default -> null;
    };
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

  public void initSandbox(@NotNull TestFixture fixture) throws IOException {
    try (final var stream = Files.newDirectoryStream(targetDirectory)) {
      for (final var path : stream) {
        final var name = path.toFile().getName();
        if (Objects.equals(name, ".idea")) {
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
  }
}