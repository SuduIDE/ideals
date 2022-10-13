package org.rri.ideals.server.references.engines;

import com.intellij.openapi.project.Project;
import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.TestEngine;
import org.rri.ideals.server.TestLexer;
import org.rri.ideals.server.util.MiscUtil;

import java.util.*;

public class FindUsagesTestEngine extends TestEngine<FindUsagesTestEngine.FindUsagesTest> {
  public record FindUsagesTest(@NotNull ReferenceParams params,
                               @NotNull List<? extends Location> answer) implements TestEngine.Test {
  }

  public FindUsagesTestEngine(@NotNull Project project,
                              @NotNull Map<@NotNull String, @NotNull String> textsByFile,
                              @NotNull Map<@NotNull String, @NotNull List<TestLexer.Marker>> markersByFile) {
    super(project, textsByFile, markersByFile);
  }

  @Override
  @NotNull
  public List<? extends FindUsagesTest> generateTests() {
    final HashMap<String, List<ReferenceParams>> paramsInfo = new HashMap<>();
    final HashMap<String, List<Location>> locationInfo = new HashMap<>();
    for (final var entry : markersByFile.entrySet()) {
      final var path = entry.getKey();
      final var file = Optional.ofNullable(MiscUtil.resolvePsiFile(project, LspPath.fromLspUri(path)))
          .orElseThrow(() -> new RuntimeException("PsiFile is null. Path: " + path));
      final var doc = Optional.ofNullable(MiscUtil.getDocument(file))
          .orElseThrow(() -> new RuntimeException("Document is null. Path: " + path));

      for (final var marker : entry.getValue()) {
        final var id = marker.additionalData.get("id");
        if (marker.name.equals("cursor")) {
          if (!paramsInfo.containsKey(id)) {
            paramsInfo.put(id, new ArrayList<>());
          }
          paramsInfo.get(id)
              .add(new ReferenceParams(
                  new TextDocumentIdentifier(path),
                  MiscUtil.offsetToPosition(doc, marker.range.startOffset()),
                  new ReferenceContext()
              ));
        } else {
          final var range = new Range(MiscUtil.offsetToPosition(doc, marker.range.startOffset()),
              MiscUtil.offsetToPosition(doc, marker.range.endOffset()));
          if (!locationInfo.containsKey(id)) {
            locationInfo.put(id, new ArrayList<>());
          }
          locationInfo.get(id).add(new Location(path, range));
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
}
