package org.rri.ideals.server.completions;

import com.google.gson.Gson;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.ui.CoreIconManager;
import com.intellij.ui.IconManager;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.completions.util.IconUtil;
import org.rri.ideals.server.completions.util.TextEditRearranger;
import org.rri.ideals.server.completions.util.TextEditWithOffsets;
import org.rri.ideals.server.util.EditorUtil;
import org.rri.ideals.server.util.MiscUtil;
import org.rri.ideals.server.util.TextUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service(Service.Level.PROJECT)
final public class CompletionService implements Disposable {
  private static final Logger LOG = Logger.getInstance(CompletionService.class);
  @NotNull
  private final Project project;

  private final AtomicReference<CompletionData> cachedDataRef = new AtomicReference<>(CompletionData.EMPTY_DATA);

  public CompletionService(@NotNull Project project) {
    this.project = project;
  }

  @Override
  public void dispose() {
  }

  @NotNull
  public List<CompletionItem> computeCompletions(
      @NotNull LspPath path,
      @NotNull Position position,
      @NotNull CancelChecker cancelChecker) {
    LOG.info("start completion");
    try {
      var virtualFile = path.findVirtualFile();
      if (virtualFile == null) {
        LOG.warn("file not found: " + path);
        return List.of();
      }
      final var psiFile = MiscUtil.resolvePsiFile(project, path);
      assert psiFile != null;
      return doComputeCompletions(psiFile, position, cancelChecker);
    } finally {
      cancelChecker.checkCanceled();
    }
  }

  @NotNull
  public CompletionItem resolveCompletion(@NotNull CompletionItem unresolved, @NotNull CancelChecker cancelChecker) {
    LOG.info("start completion resolve");
    final var completionResolveData =
        new Gson().fromJson(unresolved.getData().toString(), CompletionItemData.class);
    try {
      return doResolve(completionResolveData.getCompletionDataVersion(),
          completionResolveData.getLookupElementIndex(), unresolved, cancelChecker);
    } finally {
      cancelChecker.checkCanceled();
    }
  }


  @NotNull
  private static List<TextEditWithOffsets> toListOfEditsWithOffsets(
      @NotNull ArrayList<@NotNull TextEdit> list,
      @NotNull Document document) {
    return list.stream().map(textEdit -> new TextEditWithOffsets(textEdit, document)).toList();
  }

  @NotNull
  private CompletionItem doResolve(int completionDataVersion, int lookupElementIndex,
                                   @NotNull CompletionItem unresolved, @NotNull CancelChecker cancelChecker) {

    Ref<Document> copyThatCalledCompletionDocRef = new Ref<>();
    Ref<Document> copyToInsertDocRef = new Ref<>();
    Ref<Integer> caretOffsetAfterInsertRef = new Ref<>();

    ArrayList<TextEdit> diff;
    int caretOffsetAfterInsert;
    Document copyToInsertDoc;
    Document copyThatCalledCompletionDoc;
    var disposable = Disposer.newDisposable();
    var cachedData = cachedDataRef.get();
    try {
      if (completionDataVersion != cachedData.version) {
        return unresolved;
      }

      prepareCompletionAndHandleInsert(
          cachedData,
          lookupElementIndex,
          cancelChecker,
          copyThatCalledCompletionDocRef,
          copyToInsertDocRef,
          caretOffsetAfterInsertRef,
          disposable);

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
      WriteCommandAction.runWriteCommandAction(
          project,
          () -> Disposer.dispose(disposable));
    }
  }

  @NotNull
  private static List<@NotNull TextEdit> toListOfTextEdits(
      @NotNull List<TextEditWithOffsets> additionalEdits,
      @NotNull Document document) {
    return additionalEdits.stream().map(editWithOffsets -> editWithOffsets.toTextEdit(document)).toList();
  }

  @SuppressWarnings("UnstableApiUsage")
  @NotNull
  private static CompletionItem createLspCompletionItem(@NotNull LookupElement lookupElement,
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

      if (IconUtil.compareIcons(icon, AllIcons.Nodes.Method) ||
          IconUtil.compareIcons(icon, AllIcons.Nodes.AbstractMethod)) {
        kind = CompletionItemKind.Method;
      } else if (IconUtil.compareIcons(icon, AllIcons.Nodes.Module)
          || IconUtil.compareIcons(icon, AllIcons.Nodes.IdeaModule)
          || IconUtil.compareIcons(icon, AllIcons.Nodes.JavaModule)
          || IconUtil.compareIcons(icon, AllIcons.Nodes.ModuleGroup)) {
        kind = CompletionItemKind.Module;
      } else if (IconUtil.compareIcons(icon, AllIcons.Nodes.Function)) {
        kind = CompletionItemKind.Function;
      } else if (IconUtil.compareIcons(icon, AllIcons.Nodes.Interface)) {
        kind = CompletionItemKind.Interface;
      } else if (IconUtil.compareIcons(icon, AllIcons.Nodes.Folder)) {
        kind = CompletionItemKind.Folder;
      } else if (IconUtil.compareIcons(icon, AllIcons.Nodes.MethodReference)) {
        kind = CompletionItemKind.Reference;
      } else if (IconUtil.compareIcons(icon, AllIcons.Nodes.TextArea)) {
        kind = CompletionItemKind.Text;
      } else if (IconUtil.compareIcons(icon, AllIcons.Nodes.Type)) {
        kind = CompletionItemKind.TypeParameter;
      } else if (IconUtil.compareIcons(icon, AllIcons.Nodes.Property)) {
        kind = CompletionItemKind.Property;
      } else if (IconUtil.compareIcons(icon, AllIcons.FileTypes.Any_type) /* todo can we find that?*/) {
        kind = CompletionItemKind.File;
      } else if (IconUtil.compareIcons(icon, AllIcons.Nodes.Enum)) {
        kind = CompletionItemKind.Enum;
      } else if (IconUtil.compareIcons(icon, AllIcons.Nodes.Variable) ||
          IconUtil.compareIcons(icon, AllIcons.Nodes.Parameter) ||
          IconUtil.compareIcons(icon, AllIcons.Nodes.NewParameter)) {
        kind = CompletionItemKind.Variable;
      } else if (IconUtil.compareIcons(icon, AllIcons.Nodes.Constant)) {
        kind = CompletionItemKind.Constant;
      } else if (
          IconUtil.compareIcons(icon, AllIcons.Nodes.Class) ||  // todo find another way for classes in java
              IconUtil.compareIcons(icon, AllIcons.Nodes.AbstractClass)) {
        kind = CompletionItemKind.Class;
      } else if (IconUtil.compareIcons(icon, AllIcons.Nodes.Field)) {
        kind = CompletionItemKind.Field;
      } else if (IconUtil.compareIcons(icon, AllIcons.Nodes.Template)) {
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


  private @NotNull List<CompletionItem> doComputeCompletions(@NotNull PsiFile psiFile,
                                                             @NotNull Position position,
                                                             @NotNull CancelChecker cancelChecker) {
    VoidCompletionProcess process = new VoidCompletionProcess();
    Ref<List<CompletionItem>> resultRef = new Ref<>();
    try {
      var lookupElementsWithMatcherRef = new Ref<List<LookupElementWithMatcher>>();
      var completionDataVersionRef = new Ref<Integer>();

      // invokeAndWait is necessary for editor creation. We can create editor only inside EDT
      ApplicationManager.getApplication().invokeAndWait(
          () -> EditorUtil.withEditor(process, psiFile,
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

                var elementsWithMatcher = compInfo.getArranger().getElementsWithMatcher();
                lookupElementsWithMatcherRef.set(elementsWithMatcher);

                var document = MiscUtil.getDocument(psiFile);
                assert document != null;

                // version and data manipulations here are thread safe because they are done inside invokeAndWait
                int newVersion = 1 + cachedDataRef.get().version;
                completionDataVersionRef.set(newVersion);

                cachedDataRef.set(
                    new CompletionData(
                        elementsWithMatcher,
                        newVersion,
                        position,
                        document.getText(),
                        psiFile.getLanguage()
                    ));
              }
          )
      );
      ReadAction.run(() -> {
        var document = MiscUtil.getDocument(psiFile);
        assert document != null;
        resultRef.set(convertLookupElementsWithMatcherToCompletionItems(
            lookupElementsWithMatcherRef.get(), document, position, completionDataVersionRef.get()));
      });
    } finally {
      WriteCommandAction.runWriteCommandAction(project, () -> Disposer.dispose(process));
    }
    return resultRef.get();
  }

  @NotNull
  private List<CompletionItem> convertLookupElementsWithMatcherToCompletionItems(
      @NotNull List<LookupElementWithMatcher> lookupElementsWithMatchers,
      @NotNull Document document,
      @NotNull Position position,
      int completionDataVersion
  ) {
    var result = new ArrayList<CompletionItem>();
    var currentCaretOffset = MiscUtil.positionToOffset(document, position);
    for (int i = 0; i < lookupElementsWithMatchers.size(); i++) {
      var lookupElementWithMatcher = lookupElementsWithMatchers.get(i);
      var item =
          createLspCompletionItem(
              lookupElementWithMatcher.lookupElement(),
              MiscUtil.with(new Range(),
                  range -> {
                    range.setStart(
                        MiscUtil.offsetToPosition(
                            document,
                            currentCaretOffset - lookupElementWithMatcher.prefixMatcher().getPrefix().length())
                    );
                    range.setEnd(position);
                  }));
      item.setData(new CompletionItemData(completionDataVersion, i));
      result.add(item);
    }
    return result;
  }

  private void prepareCompletionAndHandleInsert(
      @NotNull CompletionService.CompletionData cachedData,
      int lookupElementIndex,
      @NotNull CancelChecker cancelChecker,
      @NotNull Ref<Document> copyThatCalledCompletionDocRef,
      @NotNull Ref<Document> copyToInsertDocRef,
      @NotNull Ref<Integer> caretOffsetAfterInsertRef,
      @NotNull Disposable disposable) {
    var cachedLookupElementWithMatcher = cachedData.lookupElementsWithMatcher.get(lookupElementIndex);
    var copyToInsertRef = new Ref<PsiFile>();
    ApplicationManager.getApplication().runReadAction(() -> {

      cancelChecker.checkCanceled();
      copyToInsertRef.set(PsiFileFactory.getInstance(project).createFileFromText(
          "copy",
          cachedData.language,
          cachedData.fileText,
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
      handleInsert(cachedData, cachedLookupElementWithMatcher, editor, copyToInsert, completionInfo);
      cancelChecker.checkCanceled();

      caretOffsetAfterInsertRef.set(editor.getCaretModel().getOffset());
    });
  }

  private record CompletionData(
      @NotNull List<LookupElementWithMatcher> lookupElementsWithMatcher,
      int version,
      @NotNull Position position,
      @NotNull String fileText, // file text at the moment of the completion invocation
      @NotNull Language language
  ) {
    public static final CompletionData EMPTY_DATA = new CompletionData(
        List.of(), 0, new Position(), "", Language.ANY);
  }

  @SuppressWarnings("UnstableApiUsage")
  private void handleInsert(@NotNull CompletionService.CompletionData cachedData,
                            @NotNull LookupElementWithMatcher cachedLookupElementWithMatcher,
                            @NotNull Editor editor,
                            @NotNull PsiFile copyToInsert,
                            @NotNull CompletionInfo completionInfo) {
    prepareCompletionInfoForInsert(completionInfo, cachedLookupElementWithMatcher);

    completionInfo.getLookup().finishLookup('\n', cachedLookupElementWithMatcher.lookupElement());

    var currentOffset = editor.getCaretModel().getOffset();

    WriteCommandAction.runWriteCommandAction(project,
        () -> {
          var context =
              CompletionUtil.createInsertionContext(
                  cachedData.lookupElementsWithMatcher.stream().map(LookupElementWithMatcher::lookupElement).toList(),
                  cachedLookupElementWithMatcher.lookupElement(),
                  '\n',
                  editor,
                  copyToInsert,
                  currentOffset,
                  CompletionUtil.calcIdEndOffset(
                      completionInfo.getInitContext().getOffsetMap(),
                      editor,
                      currentOffset),
                  completionInfo.getInitContext().getOffsetMap());

          cachedLookupElementWithMatcher.lookupElement().handleInsert(context);

        });

  }

  private void prepareCompletionInfoForInsert(@NotNull CompletionInfo completionInfo,
                                              @NotNull LookupElementWithMatcher lookupElementWithMatcher) {
    var prefixMatcher = lookupElementWithMatcher.prefixMatcher();

    completionInfo.getLookup().addItem(lookupElementWithMatcher.lookupElement(), prefixMatcher);

    completionInfo.getArranger().registerMatcher(lookupElementWithMatcher.lookupElement(), prefixMatcher);
    completionInfo.getArranger().addElement(
        lookupElementWithMatcher.lookupElement(),
        new LookupElementPresentation());
  }
}
