package org.rri.server.formatting;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.server.ManagedDocuments;
import org.rri.server.commands.ExecutorContext;
import org.rri.server.commands.LspCommand;
import org.rri.server.util.EditorUtil;
import org.rri.server.util.MiscUtil;

import java.util.List;
import java.util.function.Supplier;

final public class FormattingCommand extends LspCommand<List<? extends TextEdit>> {
  private static final Logger LOG = Logger.getInstance(FormattingCommand.class);
  @Nullable
  private final Range range;
  @NotNull
  private final FormattingOptions formattingOptions;

  public FormattingCommand(@Nullable Range range, @NotNull FormattingOptions formattingOptions) {
    this.range = range;
    this.formattingOptions = formattingOptions;
  }

  @NotNull
  private List<? extends TextEdit> createFormattingResults(@NotNull ExecutorContext context) {
    LOG.info(getMessageSupplier().get());
    return EditorUtil.differenceAfterAction(context.getPsiFile(), (copy) -> {
      var codeStyleSettings = CodeStyle.getSettings(copy);
      var indentOptions = codeStyleSettings.getIndentOptionsByFile(copy);
      indentOptions.TAB_SIZE = formattingOptions.getTabSize();
      indentOptions.INDENT_SIZE = formattingOptions.getTabSize();
      indentOptions.USE_TAB_CHARACTER = !formattingOptions.isInsertSpaces();
      CodeStyle.doWithTemporarySettings(context.getProject(), codeStyleSettings, () -> {
        var doc = MiscUtil.getDocument(context.getPsiFile());
        assert doc != null;
        TextRange textRange;
        if (range != null) {
          textRange = ManagedDocuments.toTextRange(doc, range);
        } else {
          textRange = new TextRange(0, copy.getTextLength());
        }
        ApplicationManager.getApplication().runWriteAction(() ->
            CodeStyleManager.getInstance(copy.getProject())
                .reformatText(
                    copy,
                    textRange.getStartOffset(),
                    textRange.getEndOffset()));
      });
      assert context.getCancelToken() != null;
      context.getCancelToken().checkCanceled();
      return copy;
    });
  }

  @NotNull
  @Override
  protected Supplier<@NotNull String> getMessageSupplier() {
    return () -> "Format call";
  }

  @Override
  protected boolean isCancellable() {
    return true;
  }

  @NotNull
  @Override
  protected List<? extends TextEdit> execute(@NotNull ExecutorContext context) {
    return createFormattingResults(context);
  }
}
