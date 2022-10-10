package org.rri.ideals.server.references.engines;

import com.intellij.openapi.project.Project;
import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.TestEngine;
import org.rri.ideals.server.util.MiscUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class FindUsagesTestEngine extends TestEngine<FindUsagesTestEngine.FindUsagesTest, FindUsagesTestEngine.FindUsagesMarker> {
  public static class FindUsagesTest implements Test {
    @NotNull
    private final ReferenceParams params;
    @NotNull
    private final List<? extends Location> answer;
    public FindUsagesTest(@NotNull ReferenceParams params, @NotNull List<? extends Location> answer) {
      this.params = params;
      this.answer = answer;
    }

    @NotNull
    @Override
    public ReferenceParams getParams() {
      return params;
    }

    @NotNull
    @Override
    public List<? extends Location> getAnswer() {
      return answer;
    }
  }

  protected static class FindUsagesMarker extends Marker {
    public final boolean isCursor;
    public final boolean isStart;
    @Nullable
    public final String id;

    public FindUsagesMarker(boolean isCursor, boolean isStart, @Nullable String id) {
      this.isCursor = isCursor;
      this.isStart = isStart;
      this.id = id;
    }

    public boolean isCursor() {
      return isCursor;
    }

    public boolean isStart() {
      return isStart;
    }

    public @Nullable String getId() {
      return id;
    }
  }

  public FindUsagesTestEngine(Path targetDirectory, Project project) throws IOException {
    super(project, targetDirectory, project);
  }

  @Override
  protected @NotNull List<? extends FindUsagesTest> processMarkers() {
    final Stack<FindUsagesMarker> markersStack = new Stack<>();
    final HashMap<String, List<ReferenceParams>> paramsInfo = new HashMap<>();
    final HashMap<String, List<Location>> locationInfo = new HashMap<>();
    for (final var entry : markersByFile.entrySet()) {
      final var path = entry.getKey();
      final var file = Optional.ofNullable(MiscUtil.resolvePsiFile(project, LspPath.fromLspUri(path)))
          .orElseThrow(() -> new RuntimeException("PsiFile is null. Path: " + path));
      final var doc = Optional.ofNullable(MiscUtil.getDocument(file))
          .orElseThrow(() -> new RuntimeException("Document is null. Path: " + path));

      for (final var marker : entry.getValue()) {
        if (marker.isCursor()) {
          if (!paramsInfo.containsKey(marker.getId())) {
            paramsInfo.put(marker.getId(), new ArrayList<>());
          }
          paramsInfo.get(marker.getId())
              .add(new ReferenceParams(
                  new TextDocumentIdentifier(path),
                  MiscUtil.offsetToPosition(doc, marker.getOffset()),
                  new ReferenceContext()
              ));
        } else {
          if (marker.isStart()) {
            markersStack.add(marker);
          } else {
            if (markersStack.empty()) {
              throw new RuntimeException("Start marker must be first\n"
                  + "current marker: " + marker.getId() + "\n"
                  + "in file: " + path);
            }
            final var startMarker = markersStack.pop();
            final var range = new Range(MiscUtil.offsetToPosition(doc, startMarker.getOffset()),
                MiscUtil.offsetToPosition(doc, marker.getOffset()));
            if (!locationInfo.containsKey(startMarker.getId())) {
              locationInfo.put(startMarker.getId(), new ArrayList<>());
            }
            locationInfo.get(startMarker.getId()).add(new Location(path, range));
          }
        }
      }
    }
    List<FindUsagesTest> tests = new ArrayList<>();
    for (final var entry : paramsInfo.entrySet()) {
      final var locs = locationInfo.get(entry.getKey());
      entry.getValue().forEach(param -> tests.add(new FindUsagesTest(param, locs)));
    }
    return tests;
  }

  @Override
  protected @NotNull FindUsagesMarker parseSingeMarker(String markerText) {
    if (markerText.equals("")) {
      return new FindUsagesMarker(false, false, null);
    }
    String[] elements = markerText.split("[\s\n]+");
    if (elements.length != 2 || !(elements[0].equals("location") || elements[0].equals("cursor"))) {
      throw new RuntimeException("Incorrect marker: " + markerText + ". \n"
          + "Expected: \"cursor\"/\"\"/\"location id='markerId'\"");
    }
    boolean isCursor = elements[0].equals("cursor");
    String[] idSplit = elements[1].split("=");
    if (idSplit.length != 2) {
      throw new RuntimeException("Incorrect id: " + elements[1] + ".\n"
          + "Expected id='markerId'");
    }
    String id = idSplit[1];
    if (!id.startsWith("'") || !id.endsWith("'")) {
      throw new RuntimeException("Id is incorrect. id=" + idSplit[1]);
    }
    id = id.substring(1, id.length() - 1);
    return new FindUsagesMarker(isCursor, true, id);
  }
}
