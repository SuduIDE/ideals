package org.rri.server.completions;

import com.google.common.collect.Streams;
import com.google.gson.JsonObject;
import com.intellij.codeInsight.completion.CompletionResult;
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

  @SuppressWarnings("UnstableApiUsage")
  public @NotNull Either<List<CompletionItem>, CompletionList> createCompletionResults(@NotNull PsiFile psiFile,
                                                                                       @NotNull Position position,
                                                                                       @NotNull CancelChecker cancelChecker) {
    VoidCompletionProcess process = new VoidCompletionProcess();
    Ref<List<CompletionItem>> resultRef = new Ref<>();
    try {
      EditorUtil.withEditor(process, psiFile,
          position,
          (editor) -> {
            var ideaCompletionResults = new ArrayList<CompletionResult>();
            var compInfo = new CompletionInfo(editor, project);
            var ideaCompService = com.intellij.codeInsight.completion.CompletionService.getCompletionService();
            assert ideaCompService != null;

            ideaCompService.performCompletion(compInfo.getParameters(),
                (result) -> {
                  compInfo.getLookup().addItem(result.getLookupElement(), result.getPrefixMatcher());
                  ideaCompletionResults.add(result);
                });

            ideaCompletionResults.forEach((it) -> {
              try {
                compInfo.getArranger().addElement(it);
              } catch (Exception ignored) {
              } // we just skip this element
            });
            List<LookupElement> sortedLookupElements =
                compInfo.getArranger().arrangeItems(compInfo.getLookup(), false).first;

            int currentResultIndex;
            synchronized (cachedData) {
              cachedData.cachedCaretOffset = editor.getCaretModel().getOffset();
              cachedData.cachedPosition = position;
              cachedData.cachedLookup = compInfo.getLookup();
              cachedData.cachedText = editor.getDocument().getText();
              cachedData.cachedLanguage = psiFile.getLanguage();
              currentResultIndex = ++cachedData.cachedResultIndex;
              cachedData.cachedLookupElements.clear();
              cachedData.cachedLookupElements.addAll(sortedLookupElements);
            }
            var result = Streams.zip(sortedLookupElements.stream(), ideaCompletionResults.stream(),
                ((lookupElement, completionResult) -> {
                  var compPrefix = completionResult.getPrefixMatcher().getPrefix();
                  var item =
                      createLSPCompletionItem(lookupElement, position,
                          compPrefix.length());
                  item.setFilterText(
                      compPrefix + item.getLabel()
                  );
                  return item;
                })).toList();
            for (int i = 0; i < result.size(); i++) {
              var completionItem = result.get(i);
              completionItem.setData(new Pair<>(currentResultIndex, i));
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
      var copyToDeleteID = (PsiFile) copyToInsert.copy();

      deleteID(copyToDeleteID, cachedLookupElement);

      var completionCalledCopyDoc = MiscUtil.getDocument(copyThatCalledCompletion);
      var insertedCopyDoc = MiscUtil.getDocument(copyToInsert);
      var deletedIDFileCopyDoc = MiscUtil.getDocument(copyToDeleteID);

      if (resultIndex != cachedData.cachedResultIndex) {
        return;
      }

      ApplicationManager.getApplication().runReadAction(() -> {
        var tempDisp = Disposer.newDisposable();
        try {
          EditorUtil.withEditor(tempDisp, copyToInsert, cachedData.cachedPosition,
              editor -> {
                CompletionInfo completionInfo = new CompletionInfo(editor, project);
                assert insertedCopyDoc != null;
                assert completionCalledCopyDoc != null;
                assert deletedIDFileCopyDoc != null;

                handleInsert(cachedLookupElement, editor, copyToInsert, completionInfo);
                PsiDocumentManager.getInstance(project).commitDocument(insertedCopyDoc);

                var diff = TextUtil.textEditFromDocs(deletedIDFileCopyDoc, insertedCopyDoc);
                if (diff.isEmpty()) {
                  return;
                }
                diff = sortTextEdits(diff, completionCalledCopyDoc);

                var caretOffsetAfterInsert = editor.getCaretModel().getOffset();

                var diffRangesAsOffsets = toListOfOffsets(diff, deletedIDFileCopyDoc);
                var additionalEdits = new ArrayList<TextEdit>();
                var unresolvedTextEdit = unresolved.getTextEdit().getLeft();

                var replaceElementStart = MiscUtil.positionToOffset(deletedIDFileCopyDoc,
                    unresolvedTextEdit.getRange().getStart());
                var replaceElementEnd = MiscUtil.positionToOffset(deletedIDFileCopyDoc,
                    unresolvedTextEdit.getRange().getEnd());

                var foundEditWithCaretIndex =
                    findIndexOfEditWithCaretAndCaretOffsetInsideEdit(
                        caretOffsetAfterInsert, diff, diffRangesAsOffsets).first;
                if (foundEditWithCaretIndex == -1) {
                  unresolved.getTextEdit().getLeft().setNewText("");
                  unresolved.setAdditionalTextEdits(diff);
                  return;
                }
                var rangeWithCaretStartOffset =
                    diffRangesAsOffsets.get(foundEditWithCaretIndex).first;
                var rangeWithCaretEndOffset =
                    diffRangesAsOffsets.get(foundEditWithCaretIndex).second;

                var mergeRangeEndOffset = Integer.max(replaceElementEnd, rangeWithCaretEndOffset);
                var mergeRangeStartOffset = Integer.min(replaceElementStart, rangeWithCaretStartOffset);

                var editsToMerge = getRangeIntersectEdits(
                    mergeRangeStartOffset, mergeRangeEndOffset, diff, diffRangesAsOffsets);
                var editsToMergeRangesAsOffsets = toListOfOffsets(editsToMerge, deletedIDFileCopyDoc);

                var newText = mergeOffsets(editsToMerge, editsToMergeRangesAsOffsets,
                    unresolvedTextEdit.getRange(),
                    deletedIDFileCopyDoc, completionCalledCopyDoc, additionalEdits);
                var setOfEditsToMerge = Set.of(editsToMerge.toArray());
                additionalEdits.addAll(diff.stream().filter(
                    textEdit -> !setOfEditsToMerge.contains(textEdit)).toList());

                unresolved.setAdditionalTextEdits(additionalEdits);
                unresolved.getTextEdit().getLeft().setNewText(newText);

                insertSnippet(unresolvedTextEdit, additionalEdits, completionCalledCopyDoc,
                    deletedIDFileCopyDoc, caretOffsetAfterInsert);
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
                             @NotNull Document deletedIDFileDoc,
                             int caretOffsetAfterInsert) {
    List<TextEdit> allEditsSorted = new ArrayList<>(additionalEdits);
    allEditsSorted.add(unresolvedTextEdit);
    allEditsSorted = sortTextEdits(allEditsSorted, completionCalledDoc);

    var allEditsRangesAsOffsets = toListOfOffsets(allEditsSorted, deletedIDFileDoc);
    var caretOffsetInMainTextEdit =
        findIndexOfEditWithCaretAndCaretOffsetInsideEdit(caretOffsetAfterInsert,
            allEditsSorted, allEditsRangesAsOffsets).second;

    var textWithSnippetBuilder = new StringBuilder();
    textWithSnippetBuilder.append(unresolvedTextEdit.getNewText());
    textWithSnippetBuilder.insert(caretOffsetInMainTextEdit, "$0");
    unresolvedTextEdit.setNewText(textWithSnippetBuilder.toString());
  }

  private String mergeOffsets(
      @NotNull List<@NotNull TextEdit> editsToMerge,
      @NotNull List<@NotNull Pair<@NotNull Integer, @NotNull Integer>> editsToMergeOffsets,
      @NotNull Range replaceElementRange,
      @NotNull Document sourceDoc,
      @NotNull Document insertedDoc,
      @NotNull List<TextEdit> additionalEdits
  ) {
    int lastIndexEditsToMerge = editsToMerge.size() - 1;
    var replaceElementStart = MiscUtil.positionToOffset(sourceDoc,
        replaceElementRange.getStart());
    var replaceElementEnd = MiscUtil.positionToOffset(sourceDoc,
        replaceElementRange.getEnd());

    var builder = new StringBuilder();
    int prevEnd = editsToMergeOffsets.get(0).second;

    if (replaceElementEnd < editsToMergeOffsets.get(0).first) {
      builder.append(sourceDoc.getText(), replaceElementEnd,
          editsToMergeOffsets.get(0).first);
    }

    builder.append(editsToMerge.get(0).getNewText());
    for (int i = 1; i < editsToMerge.size(); i++) {
      var rangeOffset = editsToMergeOffsets.get(i);
      int startOffset = rangeOffset.first;
      int endOffset = rangeOffset.second;
      builder.append(insertedDoc.getText(), prevEnd, startOffset);
      if (replaceElementStart >= startOffset) {
        additionalEdits.add(
            TextUtil.createDeleteTextEdit(new Range(
                editsToMerge.get(i - 1).getRange().getEnd(),
                editsToMerge.get(i).getRange().getStart()))
        );
      }
      if (replaceElementEnd <= prevEnd) {
        additionalEdits.add(
            TextUtil.createDeleteTextEdit(
                new Range(editsToMerge.get(i - 1).getRange().getEnd(),
                    editsToMerge.get(i).getRange().getStart()))
        );
      }
      prevEnd = endOffset;
      builder.append(editsToMerge.get(i).getNewText());
    }
    if (replaceElementStart > editsToMergeOffsets.get(lastIndexEditsToMerge).second) {
      builder.append(sourceDoc.getText(),
          editsToMergeOffsets.get(lastIndexEditsToMerge).second, replaceElementStart);
      additionalEdits.add(TextUtil.createDeleteTextEdit(new Range(
          editsToMerge.get(lastIndexEditsToMerge).getRange().getEnd(),
          replaceElementRange.getStart()))
      );
    }

    if (editsToMergeOffsets.get(0).first < replaceElementStart && editsToMergeOffsets.get(editsToMergeOffsets.size() - 1).second > replaceElementEnd) {
      additionalEdits.add(
          TextUtil.createDeleteTextEdit(new Range(editsToMerge.get(0).getRange().getStart(),
              replaceElementRange.getStart())));
      additionalEdits.add(
          TextUtil.createDeleteTextEdit(new Range(
              replaceElementRange.getEnd(),
              editsToMerge.get(lastIndexEditsToMerge).getRange().getEnd()
          ))
      );
    }
    return builder.toString();
  }

  @NotNull
  private ArrayList<@NotNull TextEdit> getRangeIntersectEdits(
      int mergeRangeStartOffset,
      int mergeRangeEndOffset,
      @NotNull List<@NotNull TextEdit> listOfEdits,
      @NotNull List<@NotNull Pair<@NotNull Integer, @NotNull Integer>> listOfRangesAsOffsets) {
    assert listOfEdits.size() == listOfRangesAsOffsets.size();
    var intersectingEdits = new ArrayList<TextEdit>();
    for (int i = 0; i < listOfRangesAsOffsets.size(); i++) {
      var editStartOffset = listOfRangesAsOffsets.get(i).first;
      var editEndOffset = listOfRangesAsOffsets.get(i).second;
      if (editEndOffset >= mergeRangeStartOffset && editStartOffset <= mergeRangeEndOffset) {
        intersectingEdits.add(listOfEdits.get(i));
      }
    }
    return intersectingEdits;
  }

  private Pair<Integer, Integer> findIndexOfEditWithCaretAndCaretOffsetInsideEdit(int caretOffset,
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

  private List<Pair<Integer, Integer>> toListOfOffsets(List<TextEdit> list,
                                                       Document document) {
    return list.stream().map(textEdit -> {
      var range = textEdit.getRange();
      return new Pair<>(
          MiscUtil.positionToOffset(document, range.getStart()),
          MiscUtil.positionToOffset(document, range.getEnd())
      );
    }).toList();
  }

  private List<TextEdit> sortTextEdits(List<TextEdit> list, Document document) {
    return list.stream().sorted(
        Comparator.comparingInt(textEdit ->
            MiscUtil.positionToOffset(document, textEdit.getRange().getStart()))).toList();
  }

  @SuppressWarnings("UnstableApiUsage")
  private void handleInsert(LookupElement cachedLookupElement,
                            Editor editor,
                            PsiFile copyToInsert,
                            CompletionInfo completionInfo) {
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

  private void deleteID(PsiFile copyToDeleteID, LookupElement cachedLookupElement) {
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
    resItem.setTextEdit(
        Either.forLeft(new TextEdit(new Range(MiscUtil.with(new Position(),
            positionIDStarts -> {
              positionIDStarts.setLine(position.getLine());
              positionIDStarts.setCharacter(position.getCharacter() - prefixLength);
            }),
            position),
            resItem.getLabel())));

    resItem.setDetail(presentation.getTypeText());
    resItem.setTags(tagList);

    return resItem;
  }
}
