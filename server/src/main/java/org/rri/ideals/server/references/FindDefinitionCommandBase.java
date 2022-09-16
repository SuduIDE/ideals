package org.rri.ideals.server.references;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.commands.ExecutorContext;
import org.rri.ideals.server.commands.LspCommand;
import org.rri.ideals.server.util.EditorUtil;
import org.rri.ideals.server.util.MiscUtil;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

abstract class FindDefinitionCommandBase extends LspCommand<Either<List<? extends Location>, List<? extends LocationLink>>> {
  @NotNull
  protected final Position pos;

  protected FindDefinitionCommandBase(@NotNull Position pos) {
    this.pos = pos;
  }

  @Override
  protected boolean isCancellable() {
    return false;
  }

  @Override
  protected @NotNull Either<List<? extends Location>, @NotNull List<? extends LocationLink>> execute(@NotNull ExecutorContext ctx) {
    PsiFile file = ctx.getPsiFile();
    Document doc = MiscUtil.getDocument(file);
    if (doc == null) {
      return Either.forRight(List.of());
    }

    var offset = MiscUtil.positionToOffset(doc, pos);
    PsiElement originalElem = file.findElementAt(offset);
    Range originalRange = MiscUtil.getPsiElementRange(doc, originalElem);

    var disposable = Disposer.newDisposable();
    try {
      var definitions = EditorUtil.calculateWithEditor(disposable, file, pos,
          editor -> findDefinitions(editor, offset))
          .filter(Objects::nonNull)
          .map(targetElem -> {
            if (targetElem.getContainingFile() == null) { return null; }
            Document targetDoc = targetElem.getContainingFile().equals(file)
                ? doc : MiscUtil.getDocument(targetElem.getContainingFile());
            return MiscUtil.psiElementToLocationLink(targetElem, targetDoc, originalRange);
          })
          .filter(Objects::nonNull)
          .collect(Collectors.toList());

      return Either.forRight(definitions);
    } finally {
      Disposer.dispose(disposable);
    }
  }

  @NotNull
  protected abstract Stream<PsiElement> findDefinitions(@NotNull Editor editor, int offset);
}
