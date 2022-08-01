package org.rri.server.symbol;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.jetbrains.python.psi.PyElement;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class DocumentSymbolPsiVisitor extends PsiRecursiveElementVisitor {
  private final PsiFile psiFile;
  private final CancelChecker cancelToken;
  private final Consumer<PsiElement> onElement;

  public DocumentSymbolPsiVisitor(@NotNull PsiFile psiFile,
                                  @Nullable CancelChecker cancelToken,
                                  @NotNull Consumer<@NotNull PsiElement> onElement) {
    super();
    this.psiFile = psiFile;
    this.cancelToken = cancelToken;
    this.onElement = onElement;
  }

  public void visit() {
    visitElement(psiFile);
  }

  public void visitElement(@NotNull PsiElement element) {
    if (element instanceof PyElement) {
      if (cancelToken != null) {
        cancelToken.checkCanceled();
      }
      onElement.accept(element);
      super.visitElement(element);
    }
  }

  public void visitFile(@NotNull PsiFile file) {
    throw new UnsupportedOperationException("Use visit() instead.");
  }
}
