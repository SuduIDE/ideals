package org.rri.server.formatting;

import com.intellij.CodeStyleBundle;
import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.server.commands.ExecutorContext;
import org.rri.server.commands.LspCommand;
import org.rri.server.util.MiscUtil;
import org.rri.server.util.TextUtil;

import java.util.List;
import java.util.function.Supplier;

final public class FormattingCommand extends LspCommand<List<? extends TextEdit>> {
  private static final Logger LOG = Logger.getInstance(FormattingCommand.class);
  @Nullable
  private final Range lspRange;
  @NotNull
  private final FormattingOptions formattingOptions;

  public FormattingCommand(@Nullable Range lspRange, @NotNull FormattingOptions formattingOptions) {
    this.lspRange = lspRange;
    this.formattingOptions = formattingOptions;
  }

  static private void doReformat(@NotNull PsiFile psiFile, @NotNull TextRange textRange) {
    ApplicationManager.getApplication().runWriteAction(() ->
        CodeStyleManager.getInstance(psiFile.getProject())
            .reformatText(
                psiFile,
                List.of(textRange))
    );
  }

  @NotNull
  @Override
  protected List<? extends TextEdit> execute(@NotNull ExecutorContext context) {
    // create reformat results
    LOG.info(getMessageSupplier().get());
    return TextUtil.differenceAfterAction(
        context.getPsiFile(),
        (copy) -> reformatPsiFile(context, copy));
  }

  void reformatPsiFile(@NotNull ExecutorContext context, @NotNull PsiFile psiFile) {
    CommandProcessor
        .getInstance()
        .executeCommand(
            context.getProject(),
            () -> CodeStyle.doWithTemporarySettings(
                context.getProject(),
                getConfiguredSettings(psiFile),
                () -> doReformat(psiFile, getConfiguredTextRange(psiFile))),
            // this name is necessary for ideas blackbox TextRange formatting
            CodeStyleBundle.message("process.reformat.code"),
            null);

    assert context.getCancelToken() != null;
    context.getCancelToken().checkCanceled();
  }

  @NotNull
  private TextRange getConfiguredTextRange(@NotNull PsiFile psiFile) {
    var doc = MiscUtil.getDocument(psiFile);
    assert doc != null;
    TextRange textRange;
    if (lspRange != null) {
      textRange = TextUtil.toTextRange(doc, lspRange);
    } else {
      textRange = new TextRange(0, psiFile.getTextLength());
    }
    return textRange;
  }

  @NotNull
  private CodeStyleSettings getConfiguredSettings(@NotNull PsiFile copy) {
    var codeStyleSettings =
        CodeStyleSettingsManager.getInstance().cloneSettings(CodeStyle.getSettings(copy));
    var indentOptions = codeStyleSettings.getIndentOptionsByFile(copy);

    indentOptions.TAB_SIZE = formattingOptions.getTabSize();
    indentOptions.INDENT_SIZE = formattingOptions.getTabSize();
    indentOptions.USE_TAB_CHARACTER = !formattingOptions.isInsertSpaces();

    return codeStyleSettings;
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

}
