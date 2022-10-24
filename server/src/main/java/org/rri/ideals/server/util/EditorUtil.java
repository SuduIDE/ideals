package org.rri.ideals.server.util;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;

public class EditorUtil {
  private EditorUtil() { }

  @Nullable
  private static Constructor<EditorComposite> sConstructor = null;

  @Nullable
  private static EditorComposite newEditorCompositeInstance(@NotNull final VirtualFile file,
                                                     @NotNull FileEditor @NotNull [] editors,
                                                     @NotNull FileEditorProvider @NotNull [] providers,
                                                     @NotNull FileEditorManagerEx fileEditorManager) {
    try {
      final var cached = sConstructor;

      final Constructor<EditorComposite> ctor;
      if (cached == null) {
        ctor = EditorComposite.class.getDeclaredConstructor(VirtualFile.class, List.class, FileEditorManagerEx.class);
        ctor.setAccessible(true);
        sConstructor = ctor;
      } else {
        ctor = cached;
      }
      final var editorsWithProviders = IntStream.range(0, providers.length)
          .filter(i -> editors[i] != null && providers[i] != null)
          .mapToObj(i -> new FileEditorWithProvider(editors[i], providers[i]))
          .toList();
      return ctor.newInstance(file, editorsWithProviders, fileEditorManager);
    } catch (Throwable e) {
      e.printStackTrace();
    }
    return null;
  }

  @Nullable
  public static EditorComposite newEditorComposite(@Nullable final VirtualFile file, @NotNull final Project project) {
    if (file == null) {
      return null;
    }
    final var editorProviderManager = FileEditorProviderManager.getInstance();
    final var providers = editorProviderManager.getProviders(project, file);
    if (providers.length == 0) {
      return null;
    }
    final var editors = new FileEditor[providers.length];
    for (int i = 0; i < providers.length; ++i) {
      final var provider = providers[i];
      assert provider != null;
      assert provider.accept(project, file);
      final var editor = provider.createEditor(project, file);
      editors[i] = editor;
      assert editor.isValid();
    }
    final var fileEditorManager = (FileEditorManagerEx) FileEditorManagerEx.getInstance(project);
    final var newComposite = newEditorCompositeInstance(file, editors, providers, fileEditorManager);
    final var editorHistoryManager = EditorHistoryManager.getInstance(project);
    for (int i = 0; i < editors.length; ++i) {
      final var editor = editors[i];
      final var provider = providers[i];
      final var state = editorHistoryManager.getState(file, provider);
      if (state != null) {
        editor.setState(state);
      }
    }
    return newComposite;
  }

  @NotNull
  public static Editor createEditor(@NotNull Disposable context,
                                    @NotNull PsiFile file,
                                    @NotNull Position position) {
    Document doc = MiscUtil.getDocument(file);
    EditorFactory editorFactory = EditorFactory.getInstance();

    assert doc != null;
    Editor created = editorFactory.createEditor(doc, file.getProject());
    created.getCaretModel().moveToLogicalPosition(new LogicalPosition(position.getLine(), position.getCharacter()));

    Disposer.register(context, () -> editorFactory.releaseEditor(created));

    return created;
  }


  public static void withEditor(@NotNull Disposable context,
                                @NotNull PsiFile file,
                                @NotNull Position position,
                                @NotNull Consumer<Editor> callback) {
    computeWithEditor(context, file, position, editor -> {
      callback.accept(editor);
      return null;
    });
  }

  public static <T> T computeWithEditor(@NotNull Disposable context,
                                        @NotNull PsiFile file,
                                        @NotNull Position position,
                                        @NotNull Function<Editor, T> callback) {
    Editor editor = createEditor(context, file, position);

    try {
      return callback.apply(editor);
    } catch (Exception e) {
      throw MiscUtil.wrap(e);
    }
  }

  public static @Nullable PsiElement findTargetElement(@NotNull Editor editor) {
    return TargetElementUtil.findTargetElement(editor, TargetElementUtil.getInstance().getAllAccepted());
  }
}
