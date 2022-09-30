package org.rri.ideals.server.references;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jetbrains.annotations.Nullable;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.TestEngine;
import org.rri.ideals.server.util.MiscUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class DefinitionTestEngine extends TestEngine {
  public static class DefinitionTest implements Test {
    private final DefinitionParams params;
    private final List<? extends LocationLink> answer;

    private DefinitionTest(DefinitionParams params, List<? extends LocationLink> answer) {
      this.params = params;
      this.answer = answer;
    }

    public DefinitionParams getParams() {
      return params;
    }

    public List<? extends LocationLink> getAnswer() {
      return answer;
    }
  }

  private static class DefinitionMarker extends Marker {
    private final boolean isTarget;
    @Nullable
    private final String id;
    private final boolean isStart;

    private DefinitionMarker(boolean isTarget, @Nullable String id, boolean isStart) {
      this.isTarget = isTarget;
      this.id = id;
      this.isStart = isStart;
    }

    public boolean isTarget() {
      return isTarget;
    }

    public @Nullable String getId() {
      return id;
    }

    public boolean isStart() {
      return isStart;
    }
  }

  public DefinitionTestEngine(Path directoryPath, Project project) throws IOException {
    super(directoryPath, project);
  }

  @Override
  protected List<? extends Test> processMarkers() {
    final Stack<DefinitionMarker> positionsStack = new Stack<>();
    final Map<String, Pair<Range, String>> originInfos = new HashMap<>();
    final Map<String, List<Pair<Range, String>>> targetInfos = new HashMap<>();
    for (final var entry : markersByFile.entrySet()) {
      final var path = entry.getKey();
      final var file = MiscUtil.resolvePsiFile(project, LspPath.fromLspUri(path));
      if (file == null) {
        throw new RuntimeException("PsiFile is null. Path: " + path);
      }
      final var doc = MiscUtil.getDocument(file);
      if (doc == null) {
        throw new RuntimeException("Document is null. Path: " + path);
      }
      for (final var raw : entry.getValue()) {
        if (!(raw instanceof final DefinitionMarker marker)) {
          throw new RuntimeException("Incorrect marker. DefinitionMarker expected");
        }
        if (marker.isStart()) {
          positionsStack.add(marker);
        } else {
          final var startMarker = positionsStack.pop();
          final var range = new Range(MiscUtil.offsetToPosition(doc, startMarker.getOffset()),
              MiscUtil.offsetToPosition(doc, marker.getOffset()));
          if (startMarker.isTarget()) {
            if (!targetInfos.containsKey(startMarker.getId())) {
              targetInfos.put(startMarker.getId(), new ArrayList<>());
            }
            targetInfos.get(startMarker.getId()).add(new Pair<>(range, path));
          } else {
            originInfos.put(startMarker.getId(), new Pair<>(range, path));
          }
        }
      }
    }
    final List<DefinitionTest> result = new ArrayList<>();
    for (final var entry : originInfos.entrySet()) {
      final var uri = LspPath.fromLspUri(entry.getValue().getSecond()).toLspUri();
      final var params = new DefinitionParams(new TextDocumentIdentifier(uri), entry.getValue().getFirst().getStart());
      final var locationLinks = targetInfos.get(entry.getKey()).stream()
          .map(pair -> new LocationLink(LspPath.fromLspUri(pair.getSecond()).toLspUri(),
              pair.getFirst(),
              pair.getFirst(),
              entry.getValue().getFirst())).toList();
      final var test = new DefinitionTest(params, locationLinks);
      result.add(test);
    }
    return result;
  }

  private String createErrorMessage(String markerText, String[] elements) {
    final var messageBuilder = new StringBuilder("Incorrect marker. Marker: ");
    messageBuilder.append(markerText);
    messageBuilder.append(". Get [");
    for (int i = 0; i < elements.length; ++i) {
      messageBuilder.append(elements[i]);
      if (i != elements.length - 1) {
        messageBuilder.append(", ");
      }
    }
    messageBuilder.append(']');
    return messageBuilder.toString();
  }

  @Override
  protected Marker parseSingeMarker(String markerText) {
    if (markerText.equals("")) {
      return new DefinitionMarker(false, null, false);
    }
    String[] elements = markerText.split("[\s\n]+");
    if (elements.length != 2) {
      throw new RuntimeException(createErrorMessage(markerText, elements));
    }
    if (!(elements[0].equals("origin") || elements[0].equals("target"))) {
      throw new RuntimeException("First element must be origin or target. Get: " + elements[0]);
    }
    String[] idSplit = elements[1].split("=");
    if (idSplit.length != 2) {
      throw new RuntimeException(createErrorMessage(elements[1], idSplit));
    }
    String id = idSplit[1];
    if (!id.startsWith("'") || !id.endsWith("'")) {
      throw new RuntimeException("Id is incorrect. id=" + id);
    }
    id = id.substring(1, id.length() - 1);
    return new DefinitionMarker(elements[0].equals("target"), id, true);
  }
}
