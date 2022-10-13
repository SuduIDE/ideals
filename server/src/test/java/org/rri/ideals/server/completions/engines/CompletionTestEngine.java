package org.rri.ideals.server.completions.engines;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.TestEngine;
import org.rri.ideals.server.TestLexer;
import org.rri.ideals.server.util.MiscUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CompletionTestEngine extends TestEngine<CompletionTestEngine.CompletionTest> {
  protected static final Logger LOG = Logger.getInstance(CompletionTestEngine.class);

  public CompletionTestEngine(Project project, @NotNull Map<@NotNull String, @NotNull String> textsByFile, @NotNull Map<@NotNull String, @NotNull List<TestLexer.Marker>> markersByFile) {
    super(project, textsByFile, markersByFile);
  }


  @NotNull
  public List<? extends CompletionTest> generateTests() {
    ArrayList<CompletionTest> ans = new ArrayList<>();
    for (var pathAndMarkers : this.markersByFile.entrySet()) {
      var path = pathAndMarkers.getKey();
      var markers = pathAndMarkers.getValue();
      if (markers.size() == 1) {
        var cursorMarker = markers.get(0);
        final var file = Optional.ofNullable(MiscUtil.resolvePsiFile(project, LspPath.fromLspUri(path)))
            .orElseThrow(() -> new RuntimeException("PsiFile is null. Path: " + path));
        final var doc = Optional.ofNullable(MiscUtil.getDocument(file))
            .orElseThrow(() -> new RuntimeException("Document is null. Path: " + path));
        var callPos = MiscUtil.offsetToPosition(doc, cursorMarker.range.startOffset());

        var test = new CompletionTest(
            new TextDocumentIdentifier(LspPath.fromLspUri(path).toLspUri()),
            callPos, doc.getText());
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

  public static class CompletionTest implements TestEngine.Test {
    @NotNull
    private final TextDocumentIdentifier documentIdentifier;

    @NotNull
    private final Position position;

    @NotNull
    private final String sourceText;

    @Nullable
    private String expectedText;

    public CompletionTest(@NotNull TextDocumentIdentifier documentIdentifier, @NotNull Position position, @NotNull String sourceText) {
      this.documentIdentifier = documentIdentifier;
      this.position = position;
      this.sourceText = sourceText;
    }

    @Override
    @NotNull
    public CompletionParams params() {
      return MiscUtil.with(new CompletionParams(), params -> {
        params.setPosition(position);
        params.setTextDocument(documentIdentifier);
      });
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
