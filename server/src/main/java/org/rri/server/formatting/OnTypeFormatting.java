package org.rri.server.formatting;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.EditorModificationUtilEx;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.Disposer;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextEdit;
import org.jetbrains.annotations.NotNull;
import org.rri.server.commands.ExecutorContext;
import org.rri.server.commands.LspCommand;
import org.rri.server.util.EditorUtil;
import org.rri.server.util.MiscUtil;
import org.rri.server.util.TextUtil;

import java.util.List;
import java.util.function.Supplier;

public class OnTypeFormatting extends LspCommand<List<? extends TextEdit>> {


  @SuppressWarnings({"unused", "FieldCanBeLocal"})
  @NotNull
  private final FormattingOptions formattingOptions;
  @NotNull
  private final Position position;
  @NotNull
  private final String triggerCharacter;

  public OnTypeFormatting(@NotNull Position position,
                          @NotNull FormattingOptions formattingOptions,
                          @NotNull String character) {
    this.position = position;
    this.formattingOptions = formattingOptions;
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
        ctx.getPsiFile(), copy -> {
          var dis = Disposer.newDisposable();
          EditorUtil.withEditor(dis, copy, position, editor ->
              ApplicationManager.getApplication().runWriteAction(() -> {

                var doc = MiscUtil.getDocument(copy);
                assert doc != null;

                editor.getSelectionModel().setSelection(
                    MiscUtil.positionToOffset(doc, position) - 1,
                    MiscUtil.positionToOffset(doc, position));

                EditorModificationUtilEx.deleteSelectedText(editor);

                ((EditorEx) editor).setHighlighter(
                    HighlighterFactory.createHighlighter(ctx.getProject(), ctx.getPsiFile().getVirtualFile()));

                editor.getCaretModel().moveToLogicalPosition(
                    new LogicalPosition(position.getLine(), position.getCharacter() - 1));

                TypedAction.getInstance().actionPerformed(
                    editor,
                    triggerCharacter.charAt(0),
                    com.intellij.openapi.editor.ex.util.EditorUtil.getEditorDataContext(editor));
              }));
          Disposer.dispose(dis);
        }
    );
  }
}
