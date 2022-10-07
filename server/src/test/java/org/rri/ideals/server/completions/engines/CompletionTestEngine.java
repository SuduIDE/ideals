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
import org.rri.ideals.server.util.MiscUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CompletionTestEngine extends
    TestEngine<CompletionTestEngine.CompletionTest, CompletionTestEngine.CompletionMarker> {
  protected static final Logger LOG = Logger.getInstance(CompletionTestEngine.class);

  public CompletionTestEngine(Path targetDirectory, Project project) throws IOException {
    super(targetDirectory, project);
  }

  @Override
  @NotNull
  protected List<? extends CompletionTest> processMarkers() {
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
        var callPos = MiscUtil.offsetToPosition(doc, cursorMarker.getOffset());

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

  @Override
  @NotNull
  protected CompletionMarker parseSingeMarker(@NotNull String markerText) {
    if (markerText.matches("\s*s\s*")) {
      return new SpaceMarker();
    }
    if (markerText.matches("\s*cursor\s*")) {
      return new CursorMarker();
    }
    throw new RuntimeException("unexpected marker: "+ markerText);
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
    public CompletionParams getParams() {
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
    public String getAnswer() {
      return expectedText;
    }

    public void setExpectedText(@Nullable String expectedText) {
      this.expectedText = expectedText;
    }
  }

  protected static class CompletionMarker extends TestEngine.Marker {
  }

  protected static class CursorMarker extends CompletionMarker {
  }

  protected static class SpaceMarker extends CompletionMarker implements InsertTextMarker {
    @Override
    public @NotNull String getText() {
      return " ";
    }
  }
}
