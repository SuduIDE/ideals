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
import org.rri.server.symbol.provider.DocumentSymbolProvider;
import org.rri.server.util.MiscUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Stack;

public class DocumentSymbolPsiVisitor extends PsiRecursiveElementVisitor {
  @NotNull
  private final PsiFile psiFile;
  @Nullable
  private final CancelChecker cancelToken;
  @NotNull
  private final Document document;
  @NotNull
  private final DocumentSymbolProvider provider;
  @NotNull
  private final Stack<@NotNull List<@NotNull DocumentSymbol>> children;

  public DocumentSymbolPsiVisitor(@NotNull PsiFile psiFile,
                                  @Nullable CancelChecker cancelToken,
                                  @NotNull Document document) {
    super();
    this.psiFile = psiFile;
    this.cancelToken = cancelToken;
    this.document = document;
    provider = DocumentSymbolProvider.getDocumentSymbolProvider(psiFile);
    children = new Stack<>();
    children.add(new ArrayList<>());
  }

  public @NotNull List<@NotNull DocumentSymbol> visit() {
    visitElement(psiFile);
    return children.pop();
  }

  public void visitElement(@NotNull PsiElement elem) {
    if (cancelToken != null) {
      cancelToken.checkCanceled();
    }
    final var kind = provider.symbolKind(elem);
    final var name = provider.symbolName(elem);
    DocumentSymbol docSym = null;

    if (kind != null && name != null) {
      final var range = MiscUtil.getPsiElementRange(document, elem);
      docSym = new DocumentSymbol(name, kind, range, range);
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
        lst.sort(Comparator.comparingInt(sym -> MiscUtil.positionToOffset(document, sym.getRange().getStart())));
        docSym.setChildren(List.of(lst.toArray(new DocumentSymbol[0])));
      }
    }
  }

  public void visitFile(@NotNull PsiFile file) {
    throw new UnsupportedOperationException("Use visit() instead.");
  }
}
