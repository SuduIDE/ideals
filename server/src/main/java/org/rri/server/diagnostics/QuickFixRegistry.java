package org.rri.server.diagnostics;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

class QuickFixRegistry {
  private final ConcurrentHashMap<Anchor, List<HighlightInfo.IntentionActionDescriptor>> quickFixes = new ConcurrentHashMap<>();
  private final Comparator<Position> positionComparator =
      Comparator.comparingInt(Position::getLine).thenComparingInt(Position::getCharacter);

  @NotNull
  public List<HighlightInfo.IntentionActionDescriptor> getQuickFixes(@NotNull Range range, @Nullable String toolId) {

    // simplistic implementation with full scan
    // on the expected amounts it seems fast enough
    return quickFixes.entrySet()
        .stream()
        .filter( it ->
            positionComparator.compare(range.getStart(), it.getKey().range.getStart()) >= 0 &&
            positionComparator.compare(range.getEnd(), it.getKey().range.getEnd()) <= 0
        )
        .flatMap(it -> it.getValue().stream())
        .collect(Collectors.toList());
  }

  public void registerQuickFixes(@NotNull Range range, @Nullable String toolId, List<HighlightInfo.IntentionActionDescriptor> actions) {
    quickFixes.put(new Anchor(range, toolId), actions);
  }

  public void dropQuickFixes() {
    quickFixes.clear();
  }

  private record Anchor(@NotNull Range range, @Nullable String toolId) {
  }
}
