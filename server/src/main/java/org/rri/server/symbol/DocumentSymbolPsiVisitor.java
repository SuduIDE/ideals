package org.rri.server.symbol;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.jetbrains.python.PythonLanguage;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolTag;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.KotlinLanguage;
import org.rri.server.symbol.provider.DocumentSymbolInfoProvider;
import org.rri.server.symbol.provider.JavaDocumentSymbolInfoProvider;
import org.rri.server.symbol.provider.KtDocumentSymbolInfoProvider;
import org.rri.server.symbol.provider.PyDocumentSymbolInfoProvider;
import org.rri.server.util.MiscUtil;

import java.util.*;
import java.util.function.Supplier;

class DocumentSymbolPsiVisitor extends PsiRecursiveElementVisitor {
  @NotNull
  private final PsiFile psiFile;
  @Nullable
  private final CancelChecker cancelToken;
  @NotNull
  private final Document document;
  @NotNull
  private static final Map<@NotNull String, @NotNull Supplier<@NotNull DocumentSymbolInfoProvider>> mapDocSymProvider;
  @NotNull
  private final Stack<@NotNull List<@NotNull DocumentSymbol>> children;

  static {
    mapDocSymProvider = Map.of(PythonLanguage.INSTANCE.getID(), PyDocumentSymbolInfoProvider::new,
        KotlinLanguage.INSTANCE.getID(), KtDocumentSymbolInfoProvider::new,
        JavaLanguage.INSTANCE.getID(), JavaDocumentSymbolInfoProvider::new);
  }

  public @NotNull List<@NotNull DocumentSymbol> visit() {
    if (provider == null) {
      return List.of();
    }
    children.add(new ArrayList<>());
    visitElement(psiFile);
    return children.pop();
  }

  @Nullable
  private final DocumentSymbolInfoProvider provider;

  public void visitFile(@NotNull PsiFile file) {
    throw new UnsupportedOperationException("Use visit() instead.");
  }


  public DocumentSymbolPsiVisitor(@NotNull PsiFile psiFile,
                                  @Nullable CancelChecker cancelToken,
                                  @NotNull Document document) {
    super();
    this.psiFile = psiFile;
    this.cancelToken = cancelToken;
    this.document = document;
    provider = getDocumentSymbolProvider(psiFile.getLanguage());
    children = new Stack<>();
  }

  @Nullable
  private static DocumentSymbolInfoProvider getDocumentSymbolProvider(@NotNull Language lang) {
    final var supplier = mapDocSymProvider.get(lang.getID());
    return supplier == null ? null : supplier.get();
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
        lst.sort(Comparator.comparingInt(sym -> MiscUtil.positionToOffset(document, sym.getRange().getStart())));
        docSym.setChildren(lst);
      }
    }
  }
}
