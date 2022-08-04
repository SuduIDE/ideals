package org.rri.server.symbol;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolTag;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.server.util.MiscUtil;
import org.rri.server.util.SymbolUtil;

import java.util.*;

public class DocumentSymbolPsiVisitor extends PsiRecursiveElementVisitor {
  @NotNull
  private final PsiFile psiFile;
  @Nullable
  private final CancelChecker cancelToken;
  @NotNull
  private final Document document;
  @NotNull
  private final Ref<@NotNull DocumentSymbol> root;
  @NotNull
  private final Stack<@NotNull PsiElement> ancestors;
  @NotNull
  private final Map<@NotNull PsiElement, @NotNull List<@NotNull DocumentSymbol>> children;

  public DocumentSymbolPsiVisitor(@NotNull PsiFile psiFile,
                                  @Nullable CancelChecker cancelToken,
                                  @NotNull Document document,
                                  @NotNull Ref<@NotNull DocumentSymbol> root) {
    super();
    this.psiFile = psiFile;
    this.cancelToken = cancelToken;
    this.document = document;
    this.root = root;
    ancestors = new Stack<>();
    children = new HashMap<>();
  }

  public void visit() {
    visitElement(psiFile);
  }

  public void visitElement(@NotNull PsiElement elem) {
    if (cancelToken != null) {
      cancelToken.checkCanceled();
    }
    final var kind = SymbolUtil.symbolKind(elem);
    final var name = SymbolUtil.symbolName(elem);
    DocumentSymbol docSym = null;

    if (kind != null && name != null) {
      final var range = MiscUtil.getPsiElementRange(document, elem);
      docSym = new DocumentSymbol(name, kind, range, range);
      if (SymbolUtil.isDeprecated(elem)) {
        docSym.setTags(List.of(SymbolTag.Deprecated));
      }

      if (ancestors.empty()) {
        root.set(docSym);
      } else {
        children.get(ancestors.peek()).add(docSym);
      }

      children.put(elem, new ArrayList<>());
      ancestors.add(elem);
    }

    super.visitElement(elem);

    if (docSym != null) {
      final var lst = children.get(elem);
      lst.sort(Comparator.comparingInt(sym -> MiscUtil.positionToOffset(document, sym.getRange().getStart())));
      ancestors.pop();
      if (lst.size() > 0) {
        docSym.setChildren(lst);
      }
    }
  }

  public void visitFile(@NotNull PsiFile file) {
    throw new UnsupportedOperationException("Use visit() instead.");
  }
}
