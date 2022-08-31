package org.rri.server;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.jetbrains.annotations.NotNull;
import org.rri.server.util.MiscUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public class TestUtil {
  private TestUtil() {
  }

  @SuppressWarnings("UnusedReturnValue")
  public static <T> T getNonBlockingEdt(@NotNull CompletableFuture<T> future, long timeoutMs) {
    final var mark = System.nanoTime();
    if (ApplicationManager.getApplication().isDispatchThread()) {
      waitInEdtFor(future::isDone, timeoutMs);
    }
    return MiscUtil.makeThrowsUnchecked(() -> future.get(timeoutMs, TimeUnit.MILLISECONDS));
  }

  public static void waitInEdtFor(@NotNull Supplier<Boolean> condition, long timeoutMs) {
    final var mark = System.nanoTime();
    while (!condition.get()) {
      if ((System.nanoTime() - mark) / 1_000_000 >= timeoutMs)
        throw new RuntimeException("timeout: " + timeoutMs, new TimeoutException());

      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      Thread.yield();
    }
  }

  @NotNull
  public static TextDocumentIdentifier getDocumentIdentifier(@NotNull LspPath filePath) {
    return MiscUtil.with(new TextDocumentIdentifier(),
        documentIdentifier -> documentIdentifier.setUri(filePath.toLspUri()));
  }

  @NotNull
  public static TextEdit newTextEdit(int startLine, int startCharacter, int endLine, int endCharacter, String newText) {
    return new TextEdit(newRange(startLine, startCharacter, endLine, endCharacter), newText);
  }

  @NotNull
  public static Range newRange(int startLine, int startCharacter, int endLine, int endCharacter) {
    return new Range(
        new Position(startLine, startCharacter), new Position(endLine, endCharacter));
  }

  // assuming edits don't overlap
  public static @NotNull String applyEdits(@NotNull String originalText, @NotNull Collection<TextEdit> edits) {
    final var sortedEdits = edits.stream()
        .sorted(Comparator.
            <TextEdit>comparingInt(it -> it.getRange().getStart().getLine())
            .thenComparingInt(it -> it.getRange().getStart().getLine())).toList();

    final var ranges = new ArrayList<TextRange>(sortedEdits.size());

    var currentLineOffset = 0;
    var currentLine = 0;

    for (TextEdit edit : sortedEdits) {
      Position position;

      position = edit.getRange().getStart();
      currentLineOffset = lineToOffset(originalText, position.getLine() - currentLine, currentLineOffset);
      currentLine = position.getLine();

      var rangeStart = currentLineOffset + position.getCharacter();

      position = edit.getRange().getEnd();
      currentLineOffset = lineToOffset(originalText, position.getLine() - currentLine, currentLineOffset);
      currentLine = position.getLine();

      final var rangeEnd = currentLineOffset + position.getCharacter();

      ranges.add(new TextRange(rangeStart, rangeEnd));
    }

    var result = originalText;

    for(int i = sortedEdits.size()-1; i >= 0; --i) {
      result = StringUtil.replaceSubstring(result, ranges.get(i), sortedEdits.get(i).getNewText());
    }

    return result;
  }

  private static int lineToOffset(@NotNull String originalText, int lineFromHere, int hereIndex) {
    for(int i = 0; i< lineFromHere; ++i) {
      hereIndex = originalText.indexOf('\n', hereIndex) + 1;
      if(hereIndex == 0)
        throw new IllegalArgumentException("position doesn't match text");
    }
    return hereIndex;
  }

  public static class DumbCancelChecker implements CancelChecker {

    @Override
    public void checkCanceled() {}

    @Override
    public boolean isCanceled() {
      return false;
    }
  }
}
