package org.rri.ideals.server.references.generators;

import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.engine.TestEngine;
import org.rri.ideals.server.generator.TestGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FindUsagesTestGenerator extends TestGenerator<FindUsagesTestGenerator.FindUsagesTest> {
  public record FindUsagesTest(@NotNull ReferenceParams params,
                               @NotNull List<? extends Location> expected) implements TestGenerator.Test {
  }

  public FindUsagesTestGenerator(@NotNull Map<@NotNull String, @NotNull String> textsByFile,
                                 @NotNull Map<@NotNull String, @NotNull List<TestEngine.Marker>> markersByFile,
                                 @NotNull OffsetPositionConverter converter) {
    super(textsByFile, markersByFile, converter);
  }

  @Override
  @NotNull
  public List<? extends FindUsagesTest> generateTests() {
    final HashMap<String, List<ReferenceParams>> paramsInfo = new HashMap<>();
    final HashMap<String, List<Location>> locationInfo = new HashMap<>();
    for (final var entry : markersByFile.entrySet()) {
      final var path = entry.getKey();

      for (final var marker : entry.getValue()) {
        final var id = marker.additionalData.get("id");
        if (marker.name.equals("cursor")) {
          if (!paramsInfo.containsKey(id)) {
            paramsInfo.put(id, new ArrayList<>());
          }
          paramsInfo.get(id)
              .add(new ReferenceParams(
                      new TextDocumentIdentifier(path),
                      converter.offsetToPosition(marker.range.startOffset(), path),
                      new ReferenceContext()
              ));
        } else {
          final var range = new Range(converter.offsetToPosition(marker.range.startOffset(), path),
                  converter.offsetToPosition(marker.range.endOffset(), path));
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
