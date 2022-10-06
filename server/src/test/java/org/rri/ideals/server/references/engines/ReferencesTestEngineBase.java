package org.rri.ideals.server.references.engines;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.Nullable;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.TestEngine;
import org.rri.ideals.server.util.MiscUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

abstract class ReferencesTestEngineBase<T extends ReferencesTestEngineBase.ReferencesTestBase>
    extends TestEngine<T, ReferencesTestEngineBase.ReferencesMarker> {
  protected abstract static class ReferencesTestBase implements Test {
    private final List<? extends LocationLink> answer;

    protected ReferencesTestBase(List<? extends LocationLink> answer) {
      this.answer = answer;
    }

    public List<? extends LocationLink> getAnswer() {
      return answer;
    }
  }

  protected static class ReferencesMarker extends Marker {
    private final boolean isTarget;
    @Nullable
    private final String id;
    private final boolean isStart;

    private ReferencesMarker(boolean isTarget, @Nullable String id, boolean isStart) {
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

  public ReferencesTestEngineBase(Path directoryPath, Project project) throws IOException {
    super(directoryPath, project);
  }

  @Override
  protected List<? extends T> processMarkers() {
    final Stack<ReferencesMarker> positionsStack = new Stack<>();
    final Map<String, List<Pair<Range, String>>> originInfos = new HashMap<>();
    final Map<String, List<Pair<Range, String>>> targetInfos = new HashMap<>();
    for (final var entry : markersByFile.entrySet()) {
      final var path = entry.getKey();
      final var file = Optional.ofNullable(MiscUtil.resolvePsiFile(project, LspPath.fromLspUri(path)))
          .orElseThrow(() -> new RuntimeException("PsiFile is null. Path: " + path));
      final var doc = Optional.ofNullable(MiscUtil.getDocument(file))
          .orElseThrow(() -> new RuntimeException("Document is null. Path: " + path));

      for (final var marker : entry.getValue()) {
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
            if (!originInfos.containsKey(startMarker.getId())) {
              originInfos.put(startMarker.getId(), new ArrayList<>());
            }
            originInfos.get(startMarker.getId()).add(new Pair<>(range, path));
          }
        }
      }
    }
    final List<T> result = new ArrayList<>();
    for (final var entry : originInfos.entrySet()) {
      final var locations = targetInfos.get(entry.getKey()).stream()
          .map(pair -> new Location(LspPath.fromLspUri(pair.getSecond()).toLspUri(), pair.getFirst()))
          .toList();
      entry.getValue().forEach(pair -> {
            final var uri = LspPath.fromLspUri(pair.getSecond()).toLspUri();
            final var locLinks = locations.stream()
                .map(loc -> new LocationLink(loc.getUri(), loc.getRange(), loc.getRange(), pair.getFirst()))
                .toList();
            final var test = createReferencesTest(uri, pair.getFirst().getStart(), locLinks);
            result.add(test);
          });
    }
    return result;
  }

  private String createErrorMessage(String markerText, String[] elements) {
    return "Incorrect marker. Marker: " + markerText + Arrays.stream(elements)
        .flatMap(str -> str.describeConstable().stream())
        .collect(Collectors.joining(". Get [", ", ", "]"));
  }

  @Override
  protected ReferencesMarker parseSingeMarker(String markerText) {
    if (markerText.equals("")) {
      return new ReferencesMarker(false, null, false);
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
    return new ReferencesMarker(elements[0].equals("target"), id, true);
  }

  abstract protected T createReferencesTest(String uri, Position pos, List<LocationLink> locLinks);
}
