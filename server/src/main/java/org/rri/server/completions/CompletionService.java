package org.rri.server.completions;

import com.google.gson.JsonObject;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtilEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.util.concurrency.AppExecutorUtil;
import jnr.ffi.annotations.Synchronized;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.server.LspPath;
import org.rri.server.util.EditorUtil;
import org.rri.server.util.MiscUtil;
import org.rri.server.util.TextUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

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
              cachedData.cachedCaretOffset = editor.getCaretModel().getOffset();
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
                      prefix.length());
              item.setData(new Pair<>(currentResultIndex, i));
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
            var resultIndex = jsonObject.get("first").getAsInt();
            var lookupElementIndex = jsonObject.get("second").getAsInt();
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
        try {
          EditorUtil.withEditor(tempDisp, copyToInsert, cachedData.cachedPosition,
              editor -> {
                CompletionInfo completionInfo = new CompletionInfo(editor, project);
                assert copyToInsertDoc != null;
                assert copyThatCalledCompletionDoc != null;

                handleInsert(cachedLookupElement, editor, copyToInsert, completionInfo);
                PsiDocumentManager.getInstance(project).commitDocument(copyToInsertDoc);

                var diff = TextUtil.textEditFromDocs(copyThatCalledCompletionDoc, copyToInsertDoc);
                if (diff.isEmpty()) {
                  return;
                }
                diff = sortTextEdits(diff, copyThatCalledCompletionDoc);

                var caretOffsetAfterInsert = editor.getCaretModel().getOffset();

                var diffRangesAsOffsets = toListOfOffsets(diff, copyThatCalledCompletionDoc);
                var additionalEdits = new ArrayList<TextEdit>();
                var unresolvedTextEdit = unresolved.getTextEdit().getLeft();

                var replaceElementStartOffset = MiscUtil.positionToOffset(copyThatCalledCompletionDoc,
                    unresolvedTextEdit.getRange().getStart());
                var replaceElementEndOffset = MiscUtil.positionToOffset(copyThatCalledCompletionDoc,
                    unresolvedTextEdit.getRange().getEnd());
                var x = findIndexOfEditWithCaretAndCaretOffsetInsideEdit(
                    caretOffsetAfterInsert, diff, diffRangesAsOffsets);
                int foundEditWithCaretIndex =
                    x.first;
                int snippetIndex = x.second;
                if (foundEditWithCaretIndex == -1) {
                  unresolved.getTextEdit().getLeft().setNewText("");
                  unresolved.setAdditionalTextEdits(diff);
                  return;
                }
                var rangeWithCaretStartOffset =
                    diffRangesAsOffsets.get(foundEditWithCaretIndex).first;
                var rangeWithCaretEndOffset =
                    diffRangesAsOffsets.get(foundEditWithCaretIndex).second;

                var mergeRangeStartOffset = Integer.min(replaceElementStartOffset, rangeWithCaretStartOffset);
                var mergeRangeEndOffset = Integer.max(replaceElementEndOffset, rangeWithCaretEndOffset);

//                var insertSnippetIndex = caretOffsetAfterInsert - mergeRangeStartOffset;

                var editsToMerge = new ArrayList<TextEdit>();
                var editsToMergeRangesAsOffsets = new ArrayList<Pair<Integer, Integer>>();

                findEditsToMerge(
                    mergeRangeStartOffset, mergeRangeEndOffset, diff, diffRangesAsOffsets,
                    editsToMerge, editsToMergeRangesAsOffsets);

//                String newText = mergeOffsets(
//                    unresolvedTextEdit.getRange(),
//                    replaceElementStartOffset,
//                    replaceElementEndOffset,
//                    editsToMerge,
//                    editsToMergeRangesAsOffsets,
//                    additionalEdits,
//                    copyThatCalledCompletionDoc
//                );

                StringBuilder builder = new StringBuilder();
                if (editsToMergeRangesAsOffsets.get(0).first > replaceElementStartOffset) {
                  builder.append(
                      copyThatCalledCompletionDoc.getText(),
                      replaceElementStartOffset,
                      editsToMergeRangesAsOffsets.get(0).first);
                } else {
                  additionalEdits.add(TextUtil.createDeleteTextEdit(new Range(
                      editsToMerge.get(0).getRange().getStart(),
                      unresolvedTextEdit.getRange().getStart()
                  )));
//                  insertSnippetIndex -= (replaceElementStartOffset - editsToMergeRangesAsOffsets.get(0).first);
                }
                final int size = editsToMerge.size();
                for (int i = 0; i < size; i++) {
                  var edit = editsToMerge.get(i);
                  var editOffsets = editsToMergeRangesAsOffsets.get(i);
                  if (i > 0) {
                    var prevEditOffsets = editsToMergeRangesAsOffsets.get(i - 1);
                    builder.append(copyThatCalledCompletionDoc.getText(), prevEditOffsets.second, editOffsets.first);
                  }
                  if (i == 0 && replaceElementStartOffset != mergeRangeStartOffset ||
                      i == size - 1 && replaceElementEndOffset != mergeRangeEndOffset) {
                    builder.append(edit.getNewText(), 0, snippetIndex);
                    builder.append("$0");
                    builder.append(edit.getNewText(), snippetIndex, edit.getNewText().length());
                  } else {
                    builder.append(edit.getNewText());
                  }
                }
                if (editsToMergeRangesAsOffsets.get(editsToMergeRangesAsOffsets.size() - 1).second < replaceElementEndOffset) {
                  builder.append(copyThatCalledCompletionDoc.getText(),
                      editsToMergeRangesAsOffsets.get(editsToMergeRangesAsOffsets.size() - 1).second,
                      replaceElementStartOffset);
                } else {
                  additionalEdits.add(TextUtil.createDeleteTextEdit(new Range(
                      unresolvedTextEdit.getRange().getEnd(),
                      editsToMerge.get(editsToMerge.size() - 1).getRange().getEnd()
                  )));
                }

                var setOfEditsToMerge = Set.of(editsToMerge.toArray());
                additionalEdits.addAll(diff.stream().filter(
                    textEdit -> !setOfEditsToMerge.contains(textEdit)).toList());

                unresolvedTextEdit.setNewText(builder.toString());
                unresolved.setAdditionalTextEdits(additionalEdits);

//                var textWithSnippetBuilder = new StringBuilder();
//                textWithSnippetBuilder.append(unresolvedTextEdit.getNewText());
//                textWithSnippetBuilder.insert(insertSnippetIndex, "$0");
//                unresolvedTextEdit.setNewText(textWithSnippetBuilder.toString());

//                insertSnippet(unresolvedTextEdit, additionalEdits, copyThatCalledCompletionDoc, caretOffsetAfterInsert);
              });
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

    private int cachedCaretOffset = 0;
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

  private void insertSnippet(@NotNull TextEdit unresolvedTextEdit,
                             @NotNull List<@NotNull TextEdit> additionalEdits,
                             @NotNull Document completionCalledDoc,
                             int caretOffsetAfterInsert) {
    List<TextEdit> allEditsSorted = new ArrayList<>(additionalEdits);
    allEditsSorted.add(unresolvedTextEdit);
    allEditsSorted = sortTextEdits(allEditsSorted, completionCalledDoc);

    var allEditsRangesAsOffsets = toListOfOffsets(allEditsSorted, completionCalledDoc);
    var caretOffsetInMainTextEdit =
        findIndexOfEditWithCaretAndCaretOffsetInsideEdit(caretOffsetAfterInsert,
            allEditsSorted, allEditsRangesAsOffsets).second;

    var textWithSnippetBuilder = new StringBuilder();
    textWithSnippetBuilder.append(unresolvedTextEdit.getNewText());
    textWithSnippetBuilder.insert(caretOffsetInMainTextEdit, "$0");
    unresolvedTextEdit.setNewText(textWithSnippetBuilder.toString());
  }
//  @NotNull
//  private String mergeOffsets(
//      @NotNull Range replaceRange,
//      int replaceElementStartOffset,
//      int replaceElementEndOffset,
//      @NotNull List<@NotNull TextEdit> editsToMerge,
//      @NotNull List<@NotNull Pair<@NotNull Integer, @NotNull Integer>> editsToMergeRangesAsOffsets,
//      @NotNull List<@NotNull TextEdit> additionalEdits,
//      @NotNull Document copyThatCalledCompletionDoc) {
//
//    return builder.toString();
//  }

  private void findEditsToMerge(
      int mergeRangeStartOffset,
      int mergeRangeEndOffset,
      @NotNull List<@NotNull TextEdit> listOfEdits,
      @NotNull List<@NotNull Pair<@NotNull Integer, @NotNull Integer>> listOfRangesAsOffsets,
      @NotNull List<@NotNull TextEdit> editsToMerge,
      @NotNull List<@NotNull Pair<@NotNull Integer, @NotNull Integer>> editsToMergeRangesAsOffsets
  ) {
    assert listOfEdits.size() == listOfRangesAsOffsets.size();
    for (int i = 0; i < listOfRangesAsOffsets.size(); i++) {
      var editStartOffset = listOfRangesAsOffsets.get(i).first;
      var editEndOffset = listOfRangesAsOffsets.get(i).second;
      if (editEndOffset >= mergeRangeStartOffset && editStartOffset <= mergeRangeEndOffset) {
        editsToMerge.add(listOfEdits.get(i));
        editsToMergeRangesAsOffsets.add(listOfRangesAsOffsets.get(i));
      }
    }
  }

  @NotNull
  private Pair<@NotNull Integer, @NotNull Integer> findIndexOfEditWithCaretAndCaretOffsetInsideEdit(
      int caretOffset,
      @NotNull List<@NotNull TextEdit> listOfEdits,
      @NotNull List<@NotNull Pair<@NotNull Integer, @NotNull Integer>> listOfOffsets) {
    synchronized (cachedData) {
      int foundIndexOfEditWithCaret = -1;
      var prev = listOfOffsets.get(0);
      caretOffset -= prev.first;
      int sub = listOfEdits.get(0).getNewText().length();
      caretOffset -= sub;
      if (caretOffset <= 0) {
        foundIndexOfEditWithCaret = 0;
      }
      for (int i = 1; i < listOfOffsets.size() && foundIndexOfEditWithCaret == -1; i++) {
        sub = (listOfOffsets.get(i).first - listOfOffsets.get(i - 1).second);
        caretOffset -= sub;
        if (caretOffset <= 0) {
          foundIndexOfEditWithCaret = i - 1;
          break;
        }
        sub = listOfEdits.get(i).getNewText().length();
        caretOffset -= sub;
        if (caretOffset <= 0) {
          foundIndexOfEditWithCaret = i;
          break;
        }
      }
      return new Pair<>(foundIndexOfEditWithCaret, caretOffset + sub);
    }
  }

  @NotNull
  private List<@NotNull Pair<@NotNull Integer, @NotNull Integer>> toListOfOffsets(
      @NotNull List<@NotNull TextEdit> list,
      @NotNull Document document) {
    return list.stream().map(textEdit -> {
      var range = textEdit.getRange();
      return new Pair<>(
          MiscUtil.positionToOffset(document, range.getStart()),
          MiscUtil.positionToOffset(document, range.getEnd())
      );
    }).toList();
  }

  @NotNull
  private List<TextEdit> sortTextEdits(@NotNull List<@NotNull TextEdit> list, @NotNull Document document) {
    return list.stream().sorted(
        Comparator.comparingInt(textEdit ->
            MiscUtil.positionToOffset(document, textEdit.getRange().getStart()))).toList();
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

  private void deleteID(@NotNull PsiFile copyToDeleteID, @NotNull LookupElement cachedLookupElement) {
    synchronized (cachedData) {
      ApplicationManager.getApplication().runWriteAction(() -> WriteCommandAction.runWriteCommandAction(project,
          () -> {
            var tempDisp = Disposer.newDisposable();
            try {
              EditorUtil.withEditor(tempDisp, copyToDeleteID, cachedData.cachedPosition,
                  (editor) -> {
                    assert cachedData.cachedLookup != null;
                    var prefix =
                        cachedData.cachedLookup.itemPattern(cachedLookupElement);
                    editor.getSelectionModel().setSelection(cachedData.cachedCaretOffset - prefix.length(),
                        cachedData.cachedCaretOffset);
                    EditorModificationUtilEx.deleteSelectedText(editor);
                  });
            } finally {
              Disposer.dispose(tempDisp);
            }
          }));
    }
  }

  @NotNull
  private static CompletionItem createLSPCompletionItem(@NotNull LookupElement lookupElement,
                                                        @NotNull Position position,
                                                        int prefixLength) {
    var resItem = new CompletionItem();
    var presentation = new LookupElementPresentation();

    ReadAction.run(() -> lookupElement.renderElement(presentation));

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
              positionIDStarts.setCharacter(position.getCharacter() - prefixLength);
            }),
            position),
            lookupElement.getLookupString()
        )));

    resItem.setDetail(presentation.getTypeText());
    resItem.setTags(tagList);
    return resItem;
  }
}
