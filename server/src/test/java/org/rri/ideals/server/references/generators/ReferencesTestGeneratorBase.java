package org.rri.ideals.server.references.generators;

import com.intellij.openapi.util.Pair;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.engine.TestEngine;
import org.rri.ideals.server.generator.TestGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

abstract class ReferencesTestGeneratorBase<T extends ReferencesTestGeneratorBase.ReferencesTestBase> extends TestGenerator<T> {

  protected abstract static class ReferencesTestBase implements Test {
    @NotNull
    private final List<? extends LocationLink> answer;

    protected ReferencesTestBase(@NotNull List<? extends LocationLink> answer) {
      this.answer = answer;
    }

    @Override
    public @NotNull List<? extends LocationLink> expected() {
      return answer;
    }
  }

  public ReferencesTestGeneratorBase(@NotNull Map<@NotNull String, @NotNull String> textsByFile,
                                     @NotNull Map<@NotNull String, @NotNull List<TestEngine.Marker>> markersByFile,
                                     @NotNull OffsetPositionConverter converter) {
    super(textsByFile, markersByFile, converter);
  }

  public @NotNull List<? extends T> generateTests() {
    final Map<String, List<Pair<Range, String>>> originInfos = new HashMap<>();
    final Map<String, List<Pair<Range, String>>> targetInfos = new HashMap<>();
    for (final var entry : markersByFile.entrySet()) {

      final var path = entry.getKey();
      for (final var marker : entry.getValue()) {
        final var range = new Range(converter.offsetToPosition(marker.range.startOffset(), path),
            converter.offsetToPosition(marker.range.endOffset(), path));
        final var map = marker.name.equals("target") ? targetInfos : originInfos;
        final var id = marker.additionalData.get("id");
        if (!map.containsKey(id)) {
          map.put(id, new ArrayList<>());
        }
        map.get(id).add(new Pair<>(range, path));
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

  abstract protected @NotNull T createReferencesTest(@NotNull String uri, @NotNull Position pos, @NotNull List<? extends LocationLink> locLinks);
}
