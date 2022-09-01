package org.rri.server.symbol;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolTag;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.server.util.MiscUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Stack;

class DocumentSymbolPsiVisitor extends PsiRecursiveElementVisitor {
  @NotNull
  private final PsiFile psiFile;
  @Nullable
  private final CancelChecker cancelToken;
  @NotNull
  private final Document document;
  @NotNull
  private final Stack<@NotNull List<@NotNull DocumentSymbol>> children;
  @Nullable
  private final DocumentSymbolInfoProvider provider;

  public DocumentSymbolPsiVisitor(@NotNull PsiFile psiFile,
                                  @Nullable CancelChecker cancelToken,
                                  @NotNull Document document) {
    super();
    this.psiFile = psiFile;
    this.cancelToken = cancelToken;
    this.document = document;
    provider = DocumentSymbolInfoProvider.findFor(psiFile.getLanguage());
    children = new Stack<>();
  }

  public @NotNull List<@NotNull DocumentSymbol> visit() {
    if (provider == null) {
      return List.of();
    }
    children.add(new ArrayList<>());
    visitElement(psiFile);
    return children.pop();
  }

  public void visitFile(@NotNull PsiFile file) {
    throw new UnsupportedOperationException("Use visit() instead.");
  }

  public void visitElement(@NotNull PsiElement elem) {
    if (cancelToken != null) {
      cancelToken.checkCanceled();
    }
    assert provider != null;
    final var info = provider.calculateSymbolInfo(elem);
    DocumentSymbol docSym = null;

    if (info != null) {
      final var range = MiscUtil.getPsiElementRange(document, elem);
      docSym = new DocumentSymbol(info.getName(), info.getKind(), range, range);
      if (provider.isDeprecated(elem)) {
        docSym.setTags(List.of(SymbolTag.Deprecated));
      }

      children.peek().add(docSym);
      children.add(new ArrayList<>());
    }

    super.visitElement(elem);

    if (docSym != null) {
      final var lst = children.pop();
      if (lst.size() > 0) {
        lst.sort(Comparator.<DocumentSymbol>comparingInt(it -> it.getRange().getStart().getLine())
            .thenComparingInt(it -> it.getRange().getStart().getLine()));
        docSym.setChildren(lst);
      }
    }
  }
}
