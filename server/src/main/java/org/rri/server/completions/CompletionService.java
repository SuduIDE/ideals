package org.rri.server.completions;

import com.google.gson.JsonObject;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.ui.CoreIconManager;
import com.intellij.ui.IconManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import jnr.ffi.annotations.Synchronized;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.server.LspPath;
import org.rri.server.completions.util.TextEditUtil;
import org.rri.server.util.EditorUtil;
import org.rri.server.util.MiscUtil;
import org.rri.server.util.TextUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.rri.server.util.IconUtil.compareIcons;

@Service(Service.Level.PROJECT)
final public class CompletionService implements Disposable {
  @NotNull
  private final Project project;
  private static final Logger LOG = Logger.getInstance(CompletionService.class);

  @NotNull
  @Synchronized
  private final CachedCompletionResolveData cachedData = new CachedCompletionResolveData();

  public CompletionService(@NotNull Project project) {
    this.project = project;
  }

  @NotNull
  public CompletableFuture<Either<List<CompletionItem>, CompletionList>> startCompletionCalculation(
      @NotNull LspPath path,
      @NotNull Position position) {
    LOG.info("start completion");
    var app = ApplicationManager.getApplication();
    return CompletableFutures.computeAsync(
        AppExecutorUtil.getAppExecutorService(),
        (cancelChecker) -> {
          final Ref<Either<List<CompletionItem>, CompletionList>> ref = new Ref<>();
          // invokeAndWait is necessary for editor creation. We can create editor only inside EDT
          app.invokeAndWait(
              () -> ref.set(MiscUtil.produceWithPsiFileInReadAction(
                      project,
                      path,
                  (psiFile) -> createCompletionResults(psiFile, position, cancelChecker)
                  )
              ),
              app.getDefaultModalityState()
          );
          return ref.get();
        }
    );
  }

  @Override
  public void dispose() {
  }

  public @NotNull Either<List<CompletionItem>, CompletionList> createCompletionResults(@NotNull PsiFile psiFile,
                                                                                       @NotNull Position position,
                                                                                       @NotNull CancelChecker cancelChecker) {
    VoidCompletionProcess process = new VoidCompletionProcess();
    Ref<List<CompletionItem>> resultRef = new Ref<>();
    try {
      EditorUtil.withEditor(process, psiFile,
          position,
          (editor) -> {
            var compInfo = new CompletionInfo(editor, project);
            var ideaCompService = com.intellij.codeInsight.completion.CompletionService.getCompletionService();
            assert ideaCompService != null;

            ideaCompService.performCompletion(compInfo.getParameters(),
                (result) -> {
                  compInfo.getLookup().addItem(result.getLookupElement(), result.getPrefixMatcher());
                  compInfo.getArranger().addElement(result);
                });

            int currentResultIndex;
            synchronized (cachedData) {
              cachedData.cachedPosition = position;
              cachedData.cachedLookup = compInfo.getLookup();
              cachedData.cachedText = editor.getDocument().getText();
              cachedData.cachedLanguage = psiFile.getLanguage();
              currentResultIndex = ++cachedData.cachedResultIndex;
              cachedData.cachedLookupElements.clear();
              cachedData.cachedLookupElements.addAll(compInfo.getArranger().getLookupItems());
            }
            var result = new ArrayList<CompletionItem>();
            for (int i = 0; i < compInfo.getArranger().getLookupItems().size(); i++) {
              var lookupElement = compInfo.getArranger().getLookupItems().get(i);
              var prefix = compInfo.getArranger().getPrefixes().get(i);
              var item =
                  createLSPCompletionItem(lookupElement, position,
                      prefix);
              item.setData(new CompletionResolveData(currentResultIndex, i));
              result.add(item);
            }
            cancelChecker.checkCanceled();
            resultRef.set(result);
          }
      );
    } finally {
      Disposer.dispose(process);
    }

    return Either.forLeft(resultRef.get());
  }

  @NotNull
  public CompletableFuture<@NotNull CompletionItem> startCompletionResolveCalculation(@NotNull CompletionItem unresolved) {
    var app = ApplicationManager.getApplication();
    LOG.info("start completion resolve");
    return CompletableFutures.computeAsync(
        AppExecutorUtil.getAppExecutorService(),
        (cancelChecker) -> {
          app.invokeAndWait(() -> {
            JsonObject jsonObject = (JsonObject) unresolved.getData();
            var resultIndex = jsonObject.get("resultIndex").getAsInt();
            var lookupElementIndex = jsonObject.get("lookupElementIndex").getAsInt();
            doResolve(resultIndex, lookupElementIndex, unresolved);
          });
          cancelChecker.checkCanceled();
          return unresolved;
        });
  }

  private void doResolve(int resultIndex, int lookupElementIndex, @NotNull CompletionItem unresolved) {
    synchronized (cachedData) {
      var cachedLookupElement = cachedData.cachedLookupElements.get(lookupElementIndex);

      assert cachedData.cachedLanguage != null;
      var copyToInsert = PsiFileFactory.getInstance(project).createFileFromText(
          "copy",
          cachedData.cachedLanguage,
          cachedData.cachedText,
          true,
          true,
          true);
      var copyThatCalledCompletion = (PsiFile) copyToInsert.copy();

      var copyThatCalledCompletionDoc = MiscUtil.getDocument(copyThatCalledCompletion);
      var copyToInsertDoc = MiscUtil.getDocument(copyToInsert);

      if (resultIndex != cachedData.cachedResultIndex) {
        return;
      }

      ApplicationManager.getApplication().runReadAction(() -> {
        var tempDisp = Disposer.newDisposable();
        int caretOffsetAfterInsert;
        try {
          var editor =
              EditorUtil.createEditor(tempDisp, copyToInsert, cachedData.cachedPosition);


          CompletionInfo completionInfo = new CompletionInfo(editor, project);
          assert copyToInsertDoc != null;
          assert copyThatCalledCompletionDoc != null;

          handleInsert(cachedLookupElement, editor, copyToInsert, completionInfo);
          caretOffsetAfterInsert = editor.getCaretModel().getOffset();

          var diff = new ArrayList<>(TextUtil.textEditFromDocs(copyThatCalledCompletionDoc, copyToInsertDoc));
          if (diff.isEmpty()) {
            return;
          }

          var unresolvedTextEdit = unresolved.getTextEdit().getLeft();

          var replaceElementStartOffset = MiscUtil.positionToOffset(copyThatCalledCompletionDoc,
              unresolvedTextEdit.getRange().getStart());
          var replaceElementEndOffset = MiscUtil.positionToOffset(copyThatCalledCompletionDoc,
              unresolvedTextEdit.getRange().getEnd());

          var newTextAndAdditionalEdits =
              TextEditUtil.findOverlappingTextEditsInRangeFromMainTextEditToCaretAndMergeThem(
                  TextEditUtil.toListOfEditsWithOffsets(diff, copyThatCalledCompletionDoc),
                  replaceElementStartOffset, replaceElementEndOffset,
                  copyThatCalledCompletionDoc.getText(), caretOffsetAfterInsert
              );
          unresolved.setAdditionalTextEdits(
              TextEditUtil.toListOfTextEdits(newTextAndAdditionalEdits.additionalEdits(), copyThatCalledCompletionDoc)
          );
          unresolvedTextEdit.setNewText(newTextAndAdditionalEdits.mainEdit().getNewText());
        } finally {
          ApplicationManager.getApplication().runWriteAction(
              () -> WriteCommandAction.runWriteCommandAction(project, () -> Disposer.dispose(tempDisp))
          );
        }
      });
    }
  }


  private static class CachedCompletionResolveData {
    @NotNull
    private final List<@NotNull LookupElement> cachedLookupElements = new ArrayList<>();
    @Nullable
    private LookupImpl cachedLookup = null;
    private int cachedResultIndex = 0;
    @NotNull
    private Position cachedPosition = new Position();
    @NotNull
    private String cachedText = "";
    @Nullable
    private Language cachedLanguage = null;

    public CachedCompletionResolveData() {
    }
  }


  private void prepareCompletionInfoForInsert(@NotNull CompletionInfo completionInfo,
                                              @NotNull LookupElement cachedLookupElement) {
    assert cachedData.cachedLookup != null;
    var prefix = cachedData.cachedLookup.itemPattern(cachedLookupElement);

    completionInfo.getLookup().addItem(cachedLookupElement,
        new CamelHumpMatcher(prefix));

    completionInfo.getArranger().addElement(cachedLookupElement,
        new LookupElementPresentation());
  }

  @SuppressWarnings("UnstableApiUsage")
  private void handleInsert(@NotNull LookupElement cachedLookupElement,
                            @NotNull Editor editor,
                            @NotNull PsiFile copyToInsert,
                            @NotNull CompletionInfo completionInfo) {
    synchronized (cachedData) {
      prepareCompletionInfoForInsert(completionInfo, cachedLookupElement);

      completionInfo.getLookup().finishLookup('\n', cachedLookupElement);

      var currentOffset = editor.getCaretModel().getOffset();

      ApplicationManager.getApplication().runWriteAction(() ->
          WriteCommandAction.runWriteCommandAction(project,
              () -> {
                var context =
                    CompletionUtil.createInsertionContext(
                        cachedData.cachedLookupElements,
                        cachedLookupElement,
                        '\n',
                        editor,
                        copyToInsert,
                        currentOffset,
                        CompletionUtil.calcIdEndOffset(
                            completionInfo.getInitContext().getOffsetMap(),
                            editor,
                            currentOffset),
                        completionInfo.getInitContext().getOffsetMap());

                cachedLookupElement.handleInsert(context);

              }));
    }
  }

  @SuppressWarnings("UnstableApiUsage")
  @NotNull
  private static CompletionItem createLSPCompletionItem(@NotNull LookupElement lookupElement,
                                                        @NotNull Position position,
                                                        @NotNull String prefix) {
    var resItem = new CompletionItem();
    Registry.get("psi.deferIconLoading").setValue(false); // todo set this flag in server setup
    var d = Disposer.newDisposable();
    try {
      IconManager.activate(new CoreIconManager());
      var presentation = LookupElementPresentation.renderElement(lookupElement);

      StringBuilder contextInfo = new StringBuilder();
      for (var textFragment : presentation.getTailFragments()) {
        contextInfo.append(textFragment.text);
      }

      var lDetails = new CompletionItemLabelDetails();
      lDetails.setDetail(contextInfo.toString());

      var tagList = new ArrayList<CompletionItemTag>();
      if (presentation.isStrikeout()) {
        tagList.add(CompletionItemTag.Deprecated);
      }
      resItem.setInsertTextFormat(InsertTextFormat.Snippet);
      resItem.setLabel(presentation.getItemText());
      resItem.setLabelDetails(lDetails);
      resItem.setInsertTextMode(InsertTextMode.AsIs);
      resItem.setFilterText(lookupElement.getLookupString());
      resItem.setTextEdit(
          Either.forLeft(new TextEdit(new Range(
              MiscUtil.with(new Position(),
                  positionIDStarts -> {
                    positionIDStarts.setLine(position.getLine());
                    positionIDStarts.setCharacter(position.getCharacter() - prefix.length());
                  }),
              position),
              lookupElement.getLookupString()
          )));

      resItem.setDetail(presentation.getTypeText());
      resItem.setTags(tagList);

      var icon = presentation.getIcon();

      if (icon == null) {
        resItem.setKind(CompletionItemKind.Keyword);
        return resItem;
      }
      CompletionItemKind kind = null;

      if (compareIcons(icon, AllIcons.Nodes.Method) ||
          compareIcons(icon, AllIcons.Nodes.AbstractMethod)) {
        kind = CompletionItemKind.Method;
      } else if (compareIcons(icon, AllIcons.Nodes.Module)
                 || compareIcons(icon, AllIcons.Nodes.IdeaModule)
                 || compareIcons(icon, AllIcons.Nodes.JavaModule)
                 || compareIcons(icon, AllIcons.Nodes.ModuleGroup)) {
        kind = CompletionItemKind.Module;
      } else if (compareIcons(icon, AllIcons.Nodes.Function)) {
        kind = CompletionItemKind.Function;
      } else if (compareIcons(icon, AllIcons.Nodes.Interface)) {
        kind = CompletionItemKind.Interface;
      } else if (compareIcons(icon, AllIcons.Nodes.Folder)) {
        kind = CompletionItemKind.Folder;
      } else if (compareIcons(icon, AllIcons.Nodes.MethodReference)) {
        kind = CompletionItemKind.Reference;
      } else if (compareIcons(icon, AllIcons.Nodes.TextArea)) {
        kind = CompletionItemKind.Text;
      } else if (compareIcons(icon, AllIcons.Nodes.Type)) { // todo what is type parameter
        kind = CompletionItemKind.TypeParameter;
      } else if (compareIcons(icon, AllIcons.Nodes.Property)) {
        kind = CompletionItemKind.Property;
      } else if (compareIcons(icon, AllIcons.FileTypes.Any_type) /* todo can we find that?*/) {
        kind = CompletionItemKind.File;
      } else if (compareIcons(icon, AllIcons.Nodes.Enum)) {
        kind = CompletionItemKind.Enum;
      } else if (compareIcons(icon, AllIcons.Nodes.Variable) ||
                 compareIcons(icon, AllIcons.Nodes.Parameter) ||
                 compareIcons(icon, AllIcons.Nodes.NewParameter)) {
        kind = CompletionItemKind.Variable;
      } else if (compareIcons(icon, AllIcons.Nodes.Constant)) {
        kind = CompletionItemKind.Constant;
      } else if (
          lookupElement.getPsiElement() instanceof PsiClass || // todo find another way for classes in java
                 compareIcons(icon, AllIcons.Nodes.Class) ||
                 compareIcons(icon, AllIcons.Nodes.AbstractClass)) {
        kind = CompletionItemKind.Class;
      } else if (compareIcons(icon, AllIcons.Nodes.Field)) {
        kind = CompletionItemKind.Field;
      } else if (compareIcons(icon, AllIcons.Nodes.Template)) {
        kind = CompletionItemKind.Snippet;
      }
      resItem.setKind(kind);

      return resItem;
    } catch (Throwable e) {
      throw MiscUtil.wrap(e);
    } finally {
      IconManager.deactivate();
      Disposer.dispose(d);
    }
  }

  private record CompletionResolveData(int resultIndex, int lookupElementIndex) {
  }

}
