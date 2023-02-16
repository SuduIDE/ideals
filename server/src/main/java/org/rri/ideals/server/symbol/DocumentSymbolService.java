package org.rri.ideals.server.symbol;

import com.intellij.ide.structureView.*;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.symbol.util.SymbolUtil;
import org.rri.ideals.server.util.LspProgressIndicator;
import org.rri.ideals.server.util.MiscUtil;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.ide.actions.ViewStructureAction.createStructureViewModel;

@Service(Service.Level.PROJECT)
final public class DocumentSymbolService {
  @NotNull
  private final Project project;
  private static final Logger LOG = Logger.getInstance(DocumentSymbolService.class);

  public DocumentSymbolService(@NotNull Project project) {
    this.project = project;
  }

  @SuppressWarnings("deprecation")
  public @NotNull List<Either<SymbolInformation, DocumentSymbol>> computeDocumentSymbols(
      @NotNull LspPath path,
      @NotNull CancelChecker cancelChecker) {
    LOG.info("document symbol start");
    final var psiFile = MiscUtil.resolvePsiFile(project, path);
    if (psiFile == null) {
      return List.of();
    }
    var disposable = Disposer.newDisposable();
    try {
      return ProgressManager.getInstance().runProcess(() -> {

        StructureViewTreeElement root = getViewTreeElement(psiFile, disposable);
        if (root == null) {
          return List.of();
        }
        Document document = ReadAction.compute(() -> MiscUtil.getDocument(psiFile));
        assert document != null;

        var rootSymbol = processTree(root, psiFile, document);
        if (rootSymbol == null) {
          return List.of();
        }
        rootSymbol.setKind(SymbolKind.File);
        return List.of(Either.forRight(rootSymbol));
      }, new LspProgressIndicator(cancelChecker));
    } finally {
      WriteCommandAction.runWriteCommandAction(project, null, null,
          () -> Disposer.dispose(disposable), psiFile);
    }
  }

  @Nullable
  private StructureViewTreeElement getViewTreeElement(@NotNull PsiFile psiFile,
                                                      @NotNull Disposable parentDisposable) {

    var fileEditor = WriteCommandAction.runWriteCommandAction(project,
        (ThrowableComputable<FileEditor, RuntimeException>)
            () -> TextEditorProvider.getInstance().createEditor(project, psiFile.getVirtualFile()));
    Disposer.register(parentDisposable, fileEditor);
    StructureViewBuilder builder = ReadAction.compute(fileEditor::getStructureViewBuilder);
    if (builder == null) {
      return null;
    }
    StructureViewModel treeModel;
    if (builder instanceof TreeBasedStructureViewBuilder) {
      treeModel = ((TreeBasedStructureViewBuilder) builder).createStructureViewModel(EditorUtil.getEditorEx(fileEditor));
    } else {
      StructureView structureView = builder.createStructureView(fileEditor, project);
      treeModel = createStructureViewModel(project, fileEditor, structureView);
    }
    return treeModel.getRoot();
  }

  @Nullable
  private DocumentSymbol processTree(@NotNull TreeElement root,
                                     @NotNull PsiFile psiFile,
                                     @NotNull Document document) {

    var documentSymbol = ReadAction.compute(() -> {
      var curSymbol = new DocumentSymbol();
      curSymbol.setKind(SymbolUtil.getSymbolKind(root.getPresentation()));
      if (root instanceof StructureViewTreeElement viewElement) {
        var maybePsiElement = viewElement.getValue();
        curSymbol.setName(viewElement.getPresentation().getPresentableText());
        if (maybePsiElement instanceof PsiElement psiElement) {
          if (psiElement.getContainingFile() != psiFile) {
            // refers to another file
            return null;
          }
          var ideaRange = psiElement.getTextRange();
          curSymbol.setRange(new Range(
              MiscUtil.offsetToPosition(document, ideaRange.getStartOffset()),
              MiscUtil.offsetToPosition(document, ideaRange.getEndOffset())));

          var ideaPickSelectionRange = new TextRange(psiElement.getTextOffset(), psiElement.getTextOffset());
          curSymbol.setSelectionRange(new Range(
              MiscUtil.offsetToPosition(document, ideaPickSelectionRange.getStartOffset()),
              MiscUtil.offsetToPosition(document, ideaPickSelectionRange.getEndOffset())));
        }
      }
      return curSymbol;
    });
    if (documentSymbol == null) {
      return null; // if refers to another file
    }
    var children = new ArrayList<DocumentSymbol>();
    for (TreeElement child : ReadAction.compute(root::getChildren)) {
      var childSymbol = processTree(child, psiFile, document);
      if (childSymbol != null) { // if not refers to another file
        children.add(childSymbol);
      }
    }
    documentSymbol.setChildren(children);
    return documentSymbol;
  }

}
