package org.rri.server.formatting;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.application.ApplicationManager;
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
  private final Range lspRange;
  @NotNull
  private final FormattingOptions formattingOptions;

  public FormattingCommand(@Nullable Range lspRange, @NotNull FormattingOptions formattingOptions) {
    this.lspRange = lspRange;
    this.formattingOptions = formattingOptions;
  }

  static public void doReformat(@NotNull PsiFile psiFile, @NotNull TextRange textRange) {
    ApplicationManager.getApplication().runWriteAction(() ->
        CodeStyleManager.getInstance(psiFile.getProject())
            .reformatText(
                psiFile,
                textRange.getStartOffset(),
                textRange.getEndOffset()));
  }

  @NotNull
  @Override
  protected List<? extends TextEdit> execute(@NotNull ExecutorContext context) {
    return createFormattingResults(context);
  }

  @NotNull
  private List<? extends TextEdit> createFormattingResults(@NotNull ExecutorContext context) {
    LOG.info(getMessageSupplier().get());
    return EditorUtil.differenceAfterAction(context.getPsiFile(), (copy) -> reformatPsiFile(context, copy));
  }

  @NotNull
  public PsiFile reformatPsiFile(@NotNull ExecutorContext context, @NotNull PsiFile psiFile) {
    CodeStyle.doWithTemporarySettings(
        context.getProject(),
        getConfiguredSettings(psiFile),
        () -> doReformat(psiFile, getConfiguredTextRange(psiFile)));

    assert context.getCancelToken() != null;
    context.getCancelToken().checkCanceled();
    return psiFile;
  }

  @NotNull
  private TextRange getConfiguredTextRange(@NotNull PsiFile psiFile) {
    var doc = MiscUtil.getDocument(psiFile);
    assert doc != null;
    TextRange textRange;
    if (lspRange != null) {
      textRange = ManagedDocuments.toTextRange(doc, lspRange);
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
