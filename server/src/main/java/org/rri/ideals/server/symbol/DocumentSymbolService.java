package org.rri.ideals.server.symbol;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.*;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElement;
import com.intellij.ui.speedSearch.ElementFilter;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.completions.util.IconUtil;
import org.rri.ideals.server.util.LspProgressIndicator;
import org.rri.ideals.server.util.MiscUtil;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.ide.actions.ViewStructureAction.createStructureViewModel;

@Service(Service.Level.PROJECT)
final public class DocumentSymbolService {
  @NotNull
  private final Project project;
  private static final Logger LOG = Logger.getInstance(DocumentSymbolCommand.class);

  public DocumentSymbolService(@NotNull Project project) {
    this.project = project;
  }

  @SuppressWarnings("deprecation")
  public @NotNull List<Either<SymbolInformation, DocumentSymbol>> computeDocumentSymbols(
      @NotNull LspPath path,
      @NotNull CancelChecker cancelChecker) {
    final var psiFile = MiscUtil.resolvePsiFile(project, path);
    if (psiFile == null) {
      return List.of();
    }
    var disposable = Disposer.newDisposable();
    try {
      return ProgressManager.getInstance().runProcess(() -> {
        Registry.get("psi.deferIconLoading").setValue(false, disposable);

        var fileEditor = WriteCommandAction.runWriteCommandAction(project,
            (ThrowableComputable<FileEditor, RuntimeException>)
                () -> TextEditorProvider.getInstance().createEditor(project, psiFile.getVirtualFile()));
        Disposer.register(disposable, fileEditor);
        StructureViewBuilder builder = ReadAction.compute(fileEditor::getStructureViewBuilder);
        if (builder == null) return List.of();
        StructureViewModel treeModel;
        if (builder instanceof TreeBasedStructureViewBuilder) {
          treeModel = ((TreeBasedStructureViewBuilder) builder).createStructureViewModel(EditorUtil.getEditorEx(fileEditor));
        } else {
          StructureView structureView = builder.createStructureView(fileEditor, project);
          treeModel = createStructureViewModel(project, fileEditor, structureView);
        }
        StructureViewTreeElement root = treeModel.getRoot();
        var doc = ReadAction.compute(() -> MiscUtil.getDocument(psiFile));
        assert doc != null;
        var rootSymbol = processTree(root, doc);
        return List.of(Either.forRight(rootSymbol));
      }, new LspProgressIndicator(cancelChecker));
    } finally {
      WriteCommandAction.runWriteCommandAction(project, null, null,
          () -> Disposer.dispose(disposable), psiFile);
    }
  }

  @SuppressWarnings("deprecation")
  private DocumentSymbol processTree(@NotNull TreeElement root,
                           @NotNull Document document) {
    var curSymbol = new DocumentSymbol();
    ReadAction.run(() -> {
      var icon = root.getPresentation().getIcon(false);
      assert icon != null;
      curSymbol.setKind(getSymbolKind(icon));
      if (root instanceof StructureViewTreeElement viewElement) {
        var maybePsiElement = viewElement.getValue();
        curSymbol.setName(viewElement.getPresentation().getPresentableText());
        if (maybePsiElement instanceof PsiElement psiElement) {
          var ideaRange = psiElement.getTextRange();
          curSymbol.setRange(new Range(
              MiscUtil.offsetToPosition(document, ideaRange.getStartOffset()),
              MiscUtil.offsetToPosition(document, ideaRange.getEndOffset())));
        }
      }
    });
    curSymbol.setSelectionRange(curSymbol.getRange());
    var children  = new ArrayList<DocumentSymbol>();
    for (TreeElement child : ReadAction.compute(root::getChildren)) {
      children.add(processTree(child, document));
    }
    curSymbol.setChildren(children);
    return curSymbol;
  }

  private SymbolKind getSymbolKind(Icon icon) {
    SymbolKind kind = SymbolKind.Object;
    if (IconUtil.compareIcons(icon, AllIcons.Nodes.Method) ||
        IconUtil.compareIcons(icon, AllIcons.Nodes.AbstractMethod)) {
      kind = SymbolKind.Method;
    } else if (IconUtil.compareIcons(icon, AllIcons.Nodes.Module)
        || IconUtil.compareIcons(icon, AllIcons.Nodes.IdeaModule)
        || IconUtil.compareIcons(icon, AllIcons.Nodes.JavaModule)
        || IconUtil.compareIcons(icon, AllIcons.Nodes.ModuleGroup)) {
      kind = SymbolKind.Module;
    } else if (IconUtil.compareIcons(icon, AllIcons.Nodes.Function)) {
      kind = SymbolKind.Function;
    } else if (IconUtil.compareIcons(icon, AllIcons.Nodes.Interface)) {
      kind = SymbolKind.Interface;
    } else if (IconUtil.compareIcons(icon, AllIcons.Nodes.Type)) {
      kind = SymbolKind.TypeParameter;
    } else if (IconUtil.compareIcons(icon, AllIcons.Nodes.Property)) {
      kind = SymbolKind.Property;
    } else if (IconUtil.compareIcons(icon, AllIcons.FileTypes.Any_type)) {
      kind = SymbolKind.File;
    } else if (IconUtil.compareIcons(icon, AllIcons.Nodes.Enum)) {
      kind = SymbolKind.Enum;
    } else if (IconUtil.compareIcons(icon, AllIcons.Nodes.Variable) ||
        IconUtil.compareIcons(icon, AllIcons.Nodes.Parameter) ||
        IconUtil.compareIcons(icon, AllIcons.Nodes.NewParameter)) {
      kind = SymbolKind.Variable;
    } else if (IconUtil.compareIcons(icon, AllIcons.Nodes.Constant)) {
      kind = SymbolKind.Constant;
    } else if (
        IconUtil.compareIcons(icon, AllIcons.Nodes.Class) ||
            IconUtil.compareIcons(icon, AllIcons.Nodes.AbstractClass)) {
      kind = SymbolKind.Class;
    } else if (IconUtil.compareIcons(icon, AllIcons.Nodes.Field)) {
      kind = SymbolKind.Field;
    }
    return kind;
  }

  private static class FileStructurePopupFilter<T> implements ElementFilter<T> {
    private String myLastFilter;
    private final Set<Object> myVisibleParents = new HashSet<>();
    private final boolean isUnitTest = ApplicationManager.getApplication().isUnitTestMode();

    @Override
    public boolean shouldBeShowing(T value) {
      return true;
    }
  }
}
