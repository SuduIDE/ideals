package org.rri.ideals.server.references;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorFileSwapper;
import com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.ideals.server.LspPath;
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
      var definitions = EditorUtil.computeWithEditor(disposable, file, pos,
          editor -> findDefinitions(editor, offset))
          .filter(Objects::nonNull)
          .map(targetElem -> {
            if (targetElem.getContainingFile() == null) { return null; }
            final var loc = findSourceLocation(ctx.getProject(), targetElem);
            if (loc != null) {
              return new LocationLink(loc.getUri(), loc.getRange(), loc.getRange(), originalRange);
            } else {
              Document targetDoc = targetElem.getContainingFile().equals(file)
                  ? doc : MiscUtil.getDocument(targetElem.getContainingFile());
              return MiscUtil.psiElementToLocationLink(targetElem, targetDoc, originalRange);
            }
          })
          .filter(Objects::nonNull)
          .collect(Collectors.toList());

      return Either.forRight(definitions);
    } finally {
      Disposer.dispose(disposable);
    }
  }

  /**
   * Tries to find the corresponding source file location for this element.
   * <p>
   * Depends on the element contained in a library's class file and the corresponding sources jar/zip attached
   * to the library.
   */
  @Nullable
  private static Location findSourceLocation(@NotNull Project project, @NotNull PsiElement element) {
    final var file = element.getContainingFile();
    final var doc = MiscUtil.getDocument(file);
    if (doc == null) {
      return null;
    }

    final var location = MiscUtil.psiElementToLocation(element, file);
    if (location == null) {
      return null;
    }
    var disposable = Disposer.newDisposable();
    try {
      final var editor = newEditorComposite(project, file.getVirtualFile());
      if (editor == null) {
        return null;
      }
      Disposer.register(disposable, editor);

      final var psiAwareEditor = EditorFileSwapper.findSinglePsiAwareEditor(editor.getAllEditors().toArray(new FileEditor[0]));
      if (psiAwareEditor == null) {
        return location;
      }
      psiAwareEditor.getEditor().getCaretModel().moveToOffset(MiscUtil.positionToOffset(doc, location.getRange().getStart()));

      final var newFilePair = EditorFileSwapper.EP_NAME.getExtensionList().stream()
              .map(fileSwapper -> fileSwapper.getFileToSwapTo(project, editor))
              .filter(Objects::nonNull)
              .findFirst();

      if (newFilePair.isEmpty() || newFilePair.get().first == null) {
        return location;
      }

      final var sourcePsiFile = MiscUtil.resolvePsiFile(project, LspPath.fromVirtualFile(newFilePair.get().first));
      if (sourcePsiFile == null) {
        return location;
      }
      final var sourceDoc = MiscUtil.getDocument(sourcePsiFile);
      if (sourceDoc == null) {
        return location;
      }
      final var virtualFile = newFilePair.get().first;
      final var offset = newFilePair.get().first != null ? newFilePair.get().second : 0;
      return new Location(LspPath.fromVirtualFile(virtualFile).toLspUri(),
              new Range(MiscUtil.offsetToPosition(sourceDoc, offset), MiscUtil.offsetToPosition(sourceDoc, offset)));
    } finally {
      Disposer.dispose(disposable);
    }
  }

  @Nullable
  private static EditorComposite newEditorComposite(@NotNull final Project project, @Nullable final VirtualFile file) {
    if (file == null) {
      return null;
    }
    final var providers = FileEditorProviderManager.getInstance().getProviderList(project, file);
    if (providers.isEmpty()) {
      return null;
    }
    final var editorsWithProviders = providers.stream().map(
        provider -> {
          assert provider != null;
          assert provider.accept(project, file);
          final var editor = provider.createEditor(project, file);
          assert editor.isValid();
          return new FileEditorWithProvider(editor, provider);
        }).toList();
    final var fileEditorManager = (FileEditorManagerEx) FileEditorManagerEx.getInstance(project);

    return new MyEditorComposite(file, editorsWithProviders, fileEditorManager);
  }

  @SuppressWarnings("deprecation")
  private static class MyEditorComposite extends EditorWithProviderComposite {
    public MyEditorComposite(@NotNull VirtualFile file,
                             @NotNull List<FileEditorWithProvider> editorsWithProviders,
                             @NotNull FileEditorManagerEx fileEditorManager) {
      super(file, editorsWithProviders, fileEditorManager);
    }
  }

  @NotNull
  protected abstract Stream<PsiElement> findDefinitions(@NotNull Editor editor, int offset);
}
