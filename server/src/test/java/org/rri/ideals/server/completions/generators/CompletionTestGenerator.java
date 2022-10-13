package org.rri.ideals.server.completions.generators;

import com.intellij.openapi.diagnostic.Logger;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.engine.TestEngine;
import org.rri.ideals.server.generator.TestGenerator;
import org.rri.ideals.server.util.MiscUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CompletionTestGenerator extends TestGenerator<CompletionTestGenerator.CompletionTest> {
  protected static final Logger LOG = Logger.getInstance(CompletionTestGenerator.class);

  public CompletionTestGenerator(
          @NotNull Map<@NotNull String, @NotNull String> textsByFile,
          @NotNull Map<@NotNull String, @NotNull List<TestEngine.Marker>> markersByFile,
          @NotNull OffsetPositionConverter converter) {
    super(textsByFile, markersByFile, converter);
  }


  @NotNull
  public List<? extends CompletionTest> generateTests() {
    ArrayList<CompletionTest> ans = new ArrayList<>();
    for (var pathAndMarkers : this.markersByFile.entrySet()) {
      var path = pathAndMarkers.getKey();
      var markers = pathAndMarkers.getValue();
      if (markers.size() == 1) {
        var cursorMarker = markers.get(0);
        var callPos = converter.offsetToPosition(cursorMarker.range.startOffset(), path);

        var test = new CompletionTest(
            new TextDocumentIdentifier(LspPath.fromLspUri(path).toLspUri()),
            callPos, textsByFile.get(path));
        var expected = this.textsByFile.get(path + ".out");
        if (expected != null) {
          test.setExpectedText(expected);
        }
        ans.add(test);
      } else if (markers.size() > 1) {
        LOG.warn("To many markers in " + path);
      }
    }

    return ans;
  }

  public static class CompletionTest implements TestGenerator.Test {
    @NotNull
    private final String sourceText;

    @Nullable
    private String expectedText;
    @NotNull
    private final CompletionParams params;

    public CompletionTest(@NotNull TextDocumentIdentifier documentIdentifier, @NotNull Position position, @NotNull String sourceText) {
      this.sourceText = sourceText;
      this.params = MiscUtil.with(new CompletionParams(), params -> {
        params.setPosition(position);
        params.setTextDocument(documentIdentifier);
      });
    }

    @Override
    @NotNull
    public CompletionParams params() {
      return params;
    }

    @NotNull
    public String getSourceText() {
      return sourceText;
    }

    @Override
    @Nullable
    public String answer() {
      return expectedText;
    }

    public void setExpectedText(@Nullable String expectedText) {
      this.expectedText = expectedText;
    }
  }
}
