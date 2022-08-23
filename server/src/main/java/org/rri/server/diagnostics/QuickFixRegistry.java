package org.rri.server.diagnostics;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

class QuickFixRegistry {
  private static final Comparator<Position> POSITION_COMPARATOR =
      Comparator.comparingInt(Position::getLine).thenComparingInt(Position::getCharacter);

  private final ConcurrentHashMap<Anchor, List<HighlightInfo.IntentionActionDescriptor>> quickFixes = new ConcurrentHashMap<>();

  @NotNull
  public List<HighlightInfo.IntentionActionDescriptor> collectForRange(@NotNull Range range) {

    // simplistic implementation with full scan
    // on the expected amounts it seems fast enough
    return quickFixes.entrySet()
        .stream()
        .filter( it ->
            POSITION_COMPARATOR.compare(range.getStart(), it.getKey().range.getStart()) >= 0 &&
            POSITION_COMPARATOR.compare(range.getEnd(), it.getKey().range.getEnd()) <= 0
        )
        .flatMap(it -> it.getValue().stream())
        .collect(Collectors.toList());
  }

  public void registerQuickFixes(@NotNull Range range, @NotNull List<HighlightInfo.IntentionActionDescriptor> actions) {
    quickFixes.put(new Anchor(range), actions);
  }

  private record Anchor(@NotNull Range range) {
  }
}
