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
import org.jetbrains.annotations.Nullable;
import org.rri.ideals.server.commands.ExecutorContext;
import org.rri.ideals.server.commands.LspCommand;
import org.rri.ideals.server.util.EditorUtil;
import org.rri.ideals.server.util.MiscUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

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

    var ref = new AtomicReference<PsiElement[]>();
    var disposable = Disposer.newDisposable();
    try {
      EditorUtil.withEditor(disposable, file, pos, editor -> {
        var declarations = getDeclarations(editor, offset);
        ref.set(declarations);
      });
    } finally {
      Disposer.dispose(disposable);
    }
    var result = ref.get();
    if (result == null || result.length == 0) {
      return Either.forRight(List.of());
    }

    var locLst = Arrays.stream(result)
        .filter(Objects::nonNull)
        .map(targetElem -> {
          if (targetElem.getContainingFile() == null) { return null; }
          Document targetDoc = targetElem.getContainingFile().equals(file)
              ? doc : MiscUtil.getDocument(targetElem.getContainingFile());
          return MiscUtil.psiElementToLocationLink(targetElem, targetDoc, originalRange);
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

    return Either.forRight(locLst);
  }

  protected abstract PsiElement @Nullable [] getDeclarations(Editor editor, int offset);
}
