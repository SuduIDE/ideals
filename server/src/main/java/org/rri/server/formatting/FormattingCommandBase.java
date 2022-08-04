package org.rri.server.formatting;

import com.intellij.application.options.CodeStyle;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.TextEdit;
import org.jetbrains.annotations.NotNull;
import org.rri.server.commands.LspCommand;

import java.util.List;

public abstract class FormattingCommandBase extends LspCommand<List<? extends TextEdit>> {
  @NotNull
  private final FormattingOptions formattingOptions;

  protected FormattingCommandBase(@NotNull FormattingOptions formattingOptions) {
    this.formattingOptions = formattingOptions;
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

  protected void doWithTemporaryCodeStyleSettingsForFile(@NotNull PsiFile psiFile,
                                                         @NotNull Runnable action) {
    CodeStyle.doWithTemporarySettings(
        psiFile.getProject(),
        getConfiguredSettings(psiFile),
        action);
  }
}
