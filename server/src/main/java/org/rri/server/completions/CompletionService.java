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
import com.intellij.openapi.editor.Document;
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
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.server.LspPath;
import org.rri.server.completions.util.TextEditRearranger;
import org.rri.server.completions.util.TextEditWithOffsets;
import org.rri.server.util.EditorUtil;
import org.rri.server.util.MiscUtil;
import org.rri.server.util.TextUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.rri.server.completions.util.IconUtil.compareIcons;

@Service(Service.Level.PROJECT)
final public class CompletionService implements Disposable {
  private static final Logger LOG = Logger.getInstance(CompletionService.class);
  @NotNull
  private final Project project;

  private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

  private final CachedCompletionResolveData cachedData = new CachedCompletionResolveData();

  public CompletionService(@NotNull Project project) {
    this.project = project;
  }

  @NotNull
  public List<CompletionItem> applyCompletionPerform(
      @NotNull LspPath path,
      @NotNull Position position,
      @NotNull CancelChecker cancelChecker) {
    LOG.info("start completion");
    final var app = ApplicationManager.getApplication();
    final Ref<List<CompletionItem>> ref = new Ref<>();
    try {
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
    } finally {
      cancelChecker.checkCanceled();
    }
  }

  @Override
  public void dispose() {
  }

  public @NotNull List<CompletionItem> createCompletionResults(@NotNull PsiFile psiFile,
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

            cancelChecker.checkCanceled();
            ideaCompService.performCompletion(compInfo.getParameters(),
                (result) -> {
                  compInfo.getLookup().addItem(result.getLookupElement(), result.getPrefixMatcher());
                  compInfo.getArranger().addElement(result);
                });
            cancelChecker.checkCanceled();

            int currentResultIndex;
            readWriteLock.writeLock().lock();
            try {
              cachedData.position = position;
              cachedData.lookup = compInfo.getLookup();
              cachedData.text = editor.getDocument().getText();
              cachedData.language = psiFile.getLanguage();
              currentResultIndex = ++cachedData.resultIndex;
              cachedData.lookupElements.clear();
              cachedData.lookupElements.addAll(compInfo.getArranger().getLookupItems());
            } finally {
              readWriteLock.writeLock().unlock();
            }
            int currentCaretOffset = editor.getCaretModel().getOffset();
            var result = new ArrayList<CompletionItem>();
            for (int i = 0; i < compInfo.getArranger().getLookupItems().size(); i++) {
              var lookupElement = compInfo.getArranger().getLookupItems().get(i);
              var prefix = compInfo.getArranger().getPrefixes().get(i);
              var item =
                  createLSPCompletionItem(
                      lookupElement,
                      MiscUtil.with(new Range(),
                          range -> {
                            range.setStart(
                                MiscUtil.offsetToPosition(
                                    Objects.requireNonNull(MiscUtil.getDocument(psiFile)),
                                    currentCaretOffset - prefix.length())
                            );
                            range.setEnd(position);
                          }));
              item.setData(new CompletionResolveData(currentResultIndex, i));
              result.add(item);
            }
            resultRef.set(result);
          }
      );
    } finally {
      Disposer.dispose(process);
    }

    return resultRef.get();
  }

  @NotNull
  public CompletionItem applyCompletionResolve(
      @NotNull CompletionItem unresolved, @NotNull CancelChecker cancelChecker) {
    LOG.info("start completion resolve");
    JsonObject jsonObject = (JsonObject) unresolved.getData();
    var resultIndex = jsonObject.get("resultIndex").getAsInt();
    var lookupElementIndex = jsonObject.get("lookupElementIndex").getAsInt();
    try {
      return doResolve(resultIndex, lookupElementIndex, unresolved, cancelChecker);
    } finally {
      cancelChecker.checkCanceled();
    }
  }

  private void prepareCompletionAndHandleInsert(
      int lookupElementIndex,
      CancelChecker cancelChecker,
      Ref<Document> copyThatCalledCompletionDocRef,
      Ref<Document> copyToInsertDocRef,
      Ref<Integer> caretOffsetAfterInsertRef,
      Disposable disposable) {
    var cachedLookupElement = cachedData.lookupElements.get(lookupElementIndex);
    assert cachedData.language != null;
    var copyToInsertRef = new Ref<PsiFile>();
    ApplicationManager.getApplication().runReadAction(() -> {

      cancelChecker.checkCanceled();
      copyToInsertRef.set(PsiFileFactory.getInstance(project).createFileFromText(
          "copy",
          cachedData.language,
          cachedData.text,
          true,
          true,
          true));
      var copyThatCalledCompletion = (PsiFile) copyToInsertRef.get().copy();
      cancelChecker.checkCanceled();

      copyThatCalledCompletionDocRef.set(MiscUtil.getDocument(copyThatCalledCompletion));
      copyToInsertDocRef.set(MiscUtil.getDocument(copyToInsertRef.get()));
    });
    var copyToInsert = copyToInsertRef.get();

    ApplicationManager.getApplication().invokeAndWait(() -> {
      var editor = EditorUtil.createEditor(disposable, copyToInsert,
          cachedData.position);
      CompletionInfo completionInfo = new CompletionInfo(editor, project);

      cancelChecker.checkCanceled();
      handleInsert(cachedLookupElement, editor, copyToInsert, completionInfo);
      cancelChecker.checkCanceled();

      caretOffsetAfterInsertRef.set(editor.getCaretModel().getOffset());
    });
  }

  @NotNull
  static private List<TextEditWithOffsets> toListOfEditsWithOffsets(
      @NotNull ArrayList<@NotNull TextEdit> list,
      @NotNull Document document) {
    return list.stream().map(textEdit -> new TextEditWithOffsets(textEdit, document)).toList();
  }

  static private @NotNull List<@NotNull TextEdit> toListOfTextEdits(
      @NotNull List<TextEditWithOffsets> additionalEdits,
      @NotNull Document document) {
    return additionalEdits.stream().map(editWithOffsets -> editWithOffsets.toTextEdit(document)).toList();
  }

  private CompletionItem doResolve(int resultIndex, int lookupElementIndex,
                       @NotNull CompletionItem unresolved, CancelChecker cancelChecker) {

    Ref<Document> copyThatCalledCompletionDocRef = new Ref<>();
    Ref<Document> copyToInsertDocRef = new Ref<>();
    Ref<Integer> caretOffsetAfterInsertRef = new Ref<>();

    ArrayList<TextEdit> diff;
    int caretOffsetAfterInsert;
    Document copyToInsertDoc;
    Document copyThatCalledCompletionDoc;
    var disposable = Disposer.newDisposable();
    readWriteLock.readLock().lock();
    try {
      try {
        assert cachedData.language != null;
        if (resultIndex != cachedData.resultIndex) {
          return unresolved;
        }
        prepareCompletionAndHandleInsert(
            lookupElementIndex,
            cancelChecker,
            copyThatCalledCompletionDocRef,
            copyToInsertDocRef,
            caretOffsetAfterInsertRef,
            disposable);
      } finally {
        readWriteLock.readLock().unlock();
      }
      copyToInsertDoc = copyToInsertDocRef.get();
      copyThatCalledCompletionDoc = copyThatCalledCompletionDocRef.get();
      assert copyToInsertDoc != null;
      assert copyThatCalledCompletionDoc != null;

      caretOffsetAfterInsert = caretOffsetAfterInsertRef.get();
      diff = new ArrayList<>(TextUtil.textEditFromDocs(copyThatCalledCompletionDoc, copyToInsertDoc));

      if (diff.isEmpty()) {
        return unresolved;
      }

      var unresolvedTextEdit = unresolved.getTextEdit().getLeft();

      var replaceElementStartOffset =
          MiscUtil.positionToOffset(copyThatCalledCompletionDoc, unresolvedTextEdit.getRange().getStart());
      var replaceElementEndOffset =
          MiscUtil.positionToOffset(copyThatCalledCompletionDoc, unresolvedTextEdit.getRange().getEnd());

      var newTextAndAdditionalEdits =
          TextEditRearranger.findOverlappingTextEditsInRangeFromMainTextEditToCaretAndMergeThem(
              toListOfEditsWithOffsets(diff, copyThatCalledCompletionDoc),
              replaceElementStartOffset, replaceElementEndOffset,
              copyThatCalledCompletionDoc.getText(), caretOffsetAfterInsert);

      unresolved.setAdditionalTextEdits(
          toListOfTextEdits(newTextAndAdditionalEdits.additionalEdits(), copyThatCalledCompletionDoc)
      );

      unresolvedTextEdit.setNewText(newTextAndAdditionalEdits.mainEdit().getNewText());
      return unresolved;
    } finally {
      ApplicationManager.getApplication().invokeAndWait(
          () -> WriteCommandAction.runWriteCommandAction(
              project,
              () -> Disposer.dispose(disposable)));
    }
  }

  private void prepareCompletionInfoForInsert(@NotNull CompletionInfo completionInfo,
                                              @NotNull LookupElement cachedLookupElement) {
    assert cachedData.lookup != null;
    var prefix = cachedData.lookup.itemPattern(cachedLookupElement);

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
      prepareCompletionInfoForInsert(completionInfo, cachedLookupElement);

      completionInfo.getLookup().finishLookup('\n', cachedLookupElement);

      var currentOffset = editor.getCaretModel().getOffset();

      ApplicationManager.getApplication().runWriteAction(() ->
          WriteCommandAction.runWriteCommandAction(project,
              () -> {
                var context =
                    CompletionUtil.createInsertionContext(
                        cachedData.lookupElements,
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

  private static class CachedCompletionResolveData {
    @NotNull
    private final List<@NotNull LookupElement> lookupElements = new ArrayList<>();
    @Nullable
    private LookupImpl lookup = null;
    private int resultIndex = 0;
    @NotNull
    private Position position = new Position();
    @NotNull
    private String text = "";
    @Nullable
    private Language language = null;

    public CachedCompletionResolveData() {
    }
  }

  @SuppressWarnings("UnstableApiUsage")
  @NotNull
  private static CompletionItem createLSPCompletionItem(@NotNull LookupElement lookupElement,
                                                        @NotNull Range textEditRange) {
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
          Either.forLeft(new TextEdit(
              textEditRange,
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
