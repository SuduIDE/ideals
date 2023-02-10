package org.rri.ideals.server.symbol;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.ui.speedSearch.ElementFilter;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.completions.util.IconUtil;
import org.rri.ideals.server.util.MiscUtil;

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
//      var editor = WriteCommandAction.runWriteCommandAction(project,
//          (ThrowableComputable<Editor, RuntimeException>) () -> EditorUtil.createEditor(disposable, psiFile,
//              new Position(0, 0)));
      var fileEditor = WriteCommandAction.runWriteCommandAction(project,
          (ThrowableComputable<FileEditor, RuntimeException>)
              () -> TextEditorProvider.getInstance().createEditor(project, psiFile.getVirtualFile()));
      Disposer.register(disposable, fileEditor);
//      FileStructurePopup popup =
//          WriteCommandAction.runWriteCommandAction(project,
//              (ThrowableComputable<FileStructurePopup, RuntimeException>)
//                  () -> ViewStructureAction.createPopup(project, fileEditor));
//      if (popup == null) {
//        return List.of();
//      }
//      Disposer.register(disposable, popup);
//      popup.show();
//      var tree = popup.getTree();
      WriteCommandAction.runWriteCommandAction(project, null, null, () -> {
        StructureViewBuilder builder = fileEditor.getStructureViewBuilder();
        if (builder == null) return;
        StructureView structureView;
        StructureViewModel treeModel;
        if (builder instanceof TreeBasedStructureViewBuilder) {
          structureView = null;
          treeModel = ((TreeBasedStructureViewBuilder)builder).createStructureViewModel(EditorUtil.getEditorEx(fileEditor));
        }
        else {
          structureView = builder.createStructureView(fileEditor, project);
          treeModel = createStructureViewModel(project, fileEditor, structureView);
        }
        var root = treeModel.getRoot();
        for (var child : root.getChildren()) {
          var icon = child.getPresentation().getIcon(false);
          assert icon != null;
          if (IconUtil.compareIcons(icon, AllIcons.Nodes.Function)
              || IconUtil.compareIcons(icon, AllIcons.Nodes.Class)) {
            LOG.warn("daaaaaaaaaaaaamn");
          }
        }
//        LOG.warn(root.getPresentation().getPresentableText());

////        Stop code analyzer to speedup EDT
//        DaemonCodeAnalyzer.getInstance(project).disableUpdateByTimer(disposable);

//        var myTreeActionsOwner = new TreeStructureActionsOwner(treeModel);
//        myTreeActionsOwner.setActionIncluded(Sorter.ALPHA_SORTER, true);
//        var myTreeModelWrapper = new TreeModelWrapper(treeModel, myTreeActionsOwner);
//        Disposer.register(disposable, myTreeModelWrapper);
//        FilteringTreeStructure myFilteringStructure;
//        var myTreeStructure = new SmartTreeStructure(project, myTreeModelWrapper) {
//          @Override
//          public void rebuildTree() {
//            if (!ApplicationManager.getApplication().isUnitTestMode()) {
//              return;
//            }
//            ProgressManager.getInstance().computePrioritized(() -> {
//              super.rebuildTree();
//              return null;
//            });
//          }
//
//          @Override
//          public boolean isToBuildChildrenInBackground(@NotNull Object element) {
//            return getRootElement() == element;
//          }
//
//          @Override
//          protected @NotNull TreeElementWrapper createTree() {
//            return StructureViewComponent.createWrapper(myProject, myModel.getRoot(), myModel);
//          }
//
//          @Override
//          public @NonNls String toString() {
//            return "structure view tree structure(model=" + myTreeModelWrapper + ")";
//          }
//        };

//        FileStructurePopupFilter<Object> filter = new FileStructurePopupFilter<>();
//        myFilteringStructure = new FilteringTreeStructure(filter, myTreeStructure, true);
//        var myStructureTreeModel = new StructureTreeModel<>(myFilteringStructure, disposable);
//        var myAsyncTreeModel = new AsyncTreeModel(myStructureTreeModel, disposable);
//        myTreeStructure.rebuildTree();
//        myFilteringStructure.rebuild();
//        myFilteringStructure.refilter();
//        myAsyncTreeModel.accept(o -> TreeVisitor.Action.CONTINUE, false).onSuccess(treePath -> {
//          LOG.warn("sssssssssssss");
//          Field field;
//          try {
//            field = FilteringTreeStructure.class.getDeclaredField("myLeaves");
//          } catch (NoSuchFieldException e) {
//            throw new RuntimeException(e);
//          }
//          field.setAccessible(true);
//          HashSet<FilteringTreeStructure.FilteringNode> x = null;
//          try {
//            x = (HashSet<FilteringTreeStructure.FilteringNode>) field.get(myFilteringStructure);
//          } catch (IllegalAccessException e) {
//            throw new RuntimeException(e);
//          }
//          LOG.warn(x.toString());
//        });
//        myStructureTreeModel.invalidateAsync().thenRun(() -> {
//          (myAsyncTreeModel.accept(o -> TreeVisitor.Action.CONTINUE, false)).onSuccess(treePath -> {
//            Field field;
//            try {
//              field = FilteringTreeStructure.class.getDeclaredField("myLeaves");
//            } catch (NoSuchFieldException e) {
//              throw new RuntimeException(e);
//            }
//            field.setAccessible(true);
//            HashSet<FilteringTreeStructure.FilteringNode> x = null;
//            try {
//              x = (HashSet<FilteringTreeStructure.FilteringNode>) field.get(myFilteringStructure);
//            } catch (IllegalAccessException e) {
//              throw new RuntimeException(e);
//            }
//            LOG.warn(x.toString());
//          }); });

      }, psiFile);
      return  List.of();
    } finally {
      WriteCommandAction.runWriteCommandAction(project, null, null,
          () -> Disposer.dispose(disposable), psiFile);
    }
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
