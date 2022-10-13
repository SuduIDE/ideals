package org.rri.ideals.server.references.engines;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.TestEngine;
import org.rri.ideals.server.TestLexer;
import org.rri.ideals.server.util.MiscUtil;

import java.util.*;

abstract class ReferencesTestEngineBase<T extends ReferencesTestEngineBase.ReferencesTestBase> extends TestEngine<T> {

  protected abstract static class ReferencesTestBase implements Test {
    @NotNull
    private final List<? extends LocationLink> answer;

    protected ReferencesTestBase(@NotNull List<? extends LocationLink> answer) {
      this.answer = answer;
    }

    @Override
    public @NotNull List<? extends LocationLink> answer() {
      return answer;
    }
  }

  public ReferencesTestEngineBase(@NotNull Project project,
                                  @NotNull Map<@NotNull String, @NotNull String> textsByFile,
                                  @NotNull Map<@NotNull String, @NotNull List<TestLexer.Marker>> markersByFile) {
    super(project, textsByFile, markersByFile);
  }

  public @NotNull List<? extends T> generateTests() {
    final Map<String, List<Pair<Range, String>>> originInfos = new HashMap<>();
    final Map<String, List<Pair<Range, String>>> targetInfos = new HashMap<>();
    for (final var entry : markersByFile.entrySet()) {
      final var path = entry.getKey();
      final var file = Optional.ofNullable(MiscUtil.resolvePsiFile(project, LspPath.fromLspUri(path)))
          .orElseThrow(() -> new RuntimeException("PsiFile is null. Path: " + path));
      final var doc = Optional.ofNullable(MiscUtil.getDocument(file))
          .orElseThrow(() -> new RuntimeException("Document is null. Path: " + path));

      for (final var marker : entry.getValue()) {
        final var range = new Range(MiscUtil.offsetToPosition(doc, marker.range.startOffset()),
            MiscUtil.offsetToPosition(doc, marker.range.endOffset()));
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
