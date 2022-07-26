package org.rri.server.formatting;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
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
    var codeStyleSettings = CodeStyle.getSettings(context.getPsiFile());
    var temporaryCodeStyleSettings = CodeStyleSettingsManager.getInstance().cloneSettings(codeStyleSettings);

    var temporaryIndentOptions = temporaryCodeStyleSettings.getIndentOptions();
    temporaryIndentOptions.TAB_SIZE = formattingOptions.getTabSize();
    temporaryIndentOptions.USE_TAB_CHARACTER = !formattingOptions.isInsertSpaces();

    var curLang = context.getPsiFile().getLanguage();
    LOG.info(curLang.getDisplayName());

    CodeStyle.setTemporarySettings(context.getProject(), temporaryCodeStyleSettings);

    var edits = EditorUtil.differenceAfterAction(context.getPsiFile(), (copy) -> {
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
              .reformatText(copy, textRange.getStartOffset(), textRange.getEndOffset()));
    });

    CodeStyle.dropTemporarySettings(context.getProject());

    return edits;
  }

  @NotNull
  @Override
  protected Supplier<@NotNull String> getMessageSupplier() {
    return () -> "Format call";
  }

  @Override
  protected boolean isCancellable() {
    return false;
  }

  @NotNull
  @Override
  protected List<? extends TextEdit> execute(@NotNull ExecutorContext context) {
    return createFormattingResults(context);
  }
}
