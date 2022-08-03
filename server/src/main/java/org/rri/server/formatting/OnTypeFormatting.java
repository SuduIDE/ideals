package org.rri.server.formatting;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtilEx;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextEdit;
import org.jetbrains.annotations.NotNull;
import org.rri.server.commands.ExecutorContext;
import org.rri.server.util.EditorUtil;
import org.rri.server.util.MiscUtil;
import org.rri.server.util.TextUtil;

import java.util.List;
import java.util.function.Supplier;

public class OnTypeFormatting extends FormattingCommandBase {
  @NotNull
  private final Position position;
  @NotNull
  private final String triggerCharacter;

  public OnTypeFormatting(@NotNull Position position,
                          @NotNull FormattingOptions formattingOptions,
                          @NotNull String character) {
    super(formattingOptions);
    this.position = position;
    this.triggerCharacter = character;
  }

  @Override
  protected @NotNull Supplier<@NotNull String> getMessageSupplier() {
    return () -> "on type formatting";
  }

  @Override
  protected boolean isCancellable() {
    return false;
  }

  @Override
  protected List<? extends TextEdit> execute(@NotNull ExecutorContext ctx) {
    LOG.info(getMessageSupplier().get());
    return TextUtil.differenceAfterAction(
        ctx.getPsiFile(),
        copy -> {
          try {
            EditorUtil.withEditor(
                this,
                copy,
                position,
                editor -> performTypeAndReformatIfNeededInFile(copy, editor));
          } finally {
            Disposer.dispose(this);
          }
        }
    );
  }

  private void performTypeAndReformatIfNeededInFile(@NotNull PsiFile psiFile,
                                                    @NotNull Editor editor) {
    assert psiFile.getVirtualFile() == null;
    var doc = MiscUtil.getDocument(psiFile);
    assert doc != null;

    ApplicationManager.getApplication().runWriteAction(() -> {
      deleteTypedChar(editor, doc);
      PsiDocumentManager.getInstance(psiFile.getProject()).commitDocument(doc);

      if (editor instanceof EditorEx) {
        ((EditorEx) editor).setHighlighter(

            HighlighterFactory.createHighlighter(
                psiFile.getProject(),
                psiFile.getFileType())
        );
      }
      doWithTemporaryCodeStyleSettingsForFile(
          psiFile,
          () -> TypedAction.getInstance().actionPerformed(
              editor,
              triggerCharacter.charAt(0),
              com.intellij.openapi.editor.ex.util.EditorUtil.getEditorDataContext(editor)));
    });
  }

  private void deleteTypedChar(@NotNull Editor editor, @NotNull Document doc) {
    var insertedCharPos = MiscUtil.positionToOffset(doc, position) - 1;

    if (!doc.getText().substring(insertedCharPos, insertedCharPos + 1).equals(triggerCharacter)) {
      // if triggered character and actual are not the same
      throw new RuntimeException("Inserted and triggered characters are not the same");
    }

    editor.getSelectionModel().setSelection(
        insertedCharPos,
        insertedCharPos + 1);
    EditorModificationUtilEx.deleteSelectedText(editor);

    editor.getCaretModel().moveToLogicalPosition(
        new LogicalPosition(position.getLine(), position.getCharacter() - 1));
  }
}
