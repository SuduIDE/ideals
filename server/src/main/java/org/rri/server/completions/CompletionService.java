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
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
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
                      prefix);
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

  private static class TextEditWithOffsets implements Comparable<TextEditWithOffsets> {
    private final Pair<Integer, Integer> range;
    private String newText;

    public TextEditWithOffsets(Integer start, Integer end, String newText) {
      this.range = new Pair<>(start, end);
      this.newText = newText;
    }

    @Override
    public int compareTo(@NotNull CompletionService.TextEditWithOffsets otherTextEditWithOffsets) {
      int res = this.range.first - otherTextEditWithOffsets.range.first;
      if (res == 0) {
        return this.range.second - otherTextEditWithOffsets.range.second;
      }
      return res;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof TextEditWithOffsets otherEdit)) {
        return false;
      }
      return range.equals(otherEdit.range);
    }
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
        int caretOffsetAfterInsert = 0;
        try {
          var editor =
              EditorUtil.createEditor(tempDisp, copyToInsert, cachedData.cachedPosition);


          CompletionInfo completionInfo = new CompletionInfo(editor, project);
          assert copyToInsertDoc != null;
          assert copyThatCalledCompletionDoc != null;

          handleInsert(cachedLookupElement, editor, copyToInsert, completionInfo);
          caretOffsetAfterInsert = editor.getCaretModel().getOffset();
        } finally {
          ApplicationManager.getApplication().runWriteAction(
              () -> WriteCommandAction.runWriteCommandAction(project, () -> Disposer.dispose(tempDisp))
          );
        }
        var diff = new ArrayList<>(TextUtil.textEditFromDocs(copyThatCalledCompletionDoc,
            copyToInsertDoc));
        if (diff.isEmpty()) {
          return;
        }
        if (unresolved.getLabel().equals("None")) {
          LOG.warn("here");
        }

        var mapDiffOffsetsToTextEdits = toMapOfOffsetsToTextEdits(diff, copyThatCalledCompletionDoc);
        var diffRangesAsOffsets =
            new TreeSet<>(mapDiffOffsetsToTextEdits.navigableKeySet());
        var additionalEdits = new ArrayList<TextEdit>();
        var unresolvedTextEdit = unresolved.getTextEdit().getLeft();

        var replaceElementStartOffset = MiscUtil.positionToOffset(copyThatCalledCompletionDoc,
            unresolvedTextEdit.getRange().getStart());
        var replaceElementEndOffset = MiscUtil.positionToOffset(copyThatCalledCompletionDoc,
            unresolvedTextEdit.getRange().getEnd());

        int caretOffsetAcc = caretOffsetAfterInsert;

        int sub = 0;
        int prevEnd = 0;
        TextEditWithOffsets textEditWithCaret = null;
        for (var editWithOffsets : diffRangesAsOffsets) {
          sub = (editWithOffsets.range.first - prevEnd);
          prevEnd = editWithOffsets.range.second;
          caretOffsetAcc -= sub;
          if (caretOffsetAcc < 0) {
            caretOffsetAcc += sub;
            textEditWithCaret = new TextEditWithOffsets(caretOffsetAcc, caretOffsetAcc, "$0");
            break;
          }
          sub = editWithOffsets.newText.length();
          caretOffsetAcc -= sub;
          if (caretOffsetAcc <= 0) {
            caretOffsetAcc += sub;
            editWithOffsets.newText = editWithOffsets.newText.substring(0, caretOffsetAcc) +
                "$0" + editWithOffsets.newText.substring(caretOffsetAcc);
            textEditWithCaret = editWithOffsets;
            break;
          }
        }
        if (textEditWithCaret == null) {
          var caretOffsetInOriginalDoc = prevEnd + caretOffsetAcc;
          textEditWithCaret =
              new TextEditWithOffsets(
                  caretOffsetInOriginalDoc, caretOffsetInOriginalDoc, "$0");
        }
        diffRangesAsOffsets.add(textEditWithCaret);
        int selectedRangeStartOffset = textEditWithCaret.range.first;
        int selectedRangeEndOffset = textEditWithCaret.range.second;


        int mergeRangeStartOffset = Integer.min(selectedRangeStartOffset, replaceElementStartOffset);
        int mergeRangeEndOffset = Integer.max(selectedRangeEndOffset, replaceElementEndOffset);

        var first = new TextEditWithOffsets(mergeRangeStartOffset,
            mergeRangeStartOffset, "");
        var last = new TextEditWithOffsets(mergeRangeEndOffset, mergeRangeEndOffset, "");
        var floor = diffRangesAsOffsets.floor(first);
        var ceil = diffRangesAsOffsets.ceiling(last);
        var editsToMergeRangesAsOffsets = new TreeSet<>(diffRangesAsOffsets.subSet(first, true, last, true));

        if (floor != null) {
          editsToMergeRangesAsOffsets.add(floor);
          additionalEdits.addAll(mapDiffOffsetsToTextEdits.headMap(floor, false).values());
        }

        if (ceil != null) {
          editsToMergeRangesAsOffsets.add(ceil);
          additionalEdits.addAll(mapDiffOffsetsToTextEdits.tailMap(ceil, false).values());
        }

        // todo use another var
        mergeRangeStartOffset = editsToMergeRangesAsOffsets.first().range.first;
        mergeRangeEndOffset = editsToMergeRangesAsOffsets.last().range.second;
        StringBuilder builder = new StringBuilder();
        if (mergeRangeStartOffset > replaceElementStartOffset) {
          builder.append(
              copyThatCalledCompletionDoc.getText(),
              replaceElementStartOffset,
              mergeRangeStartOffset);
        } else if (mergeRangeStartOffset != replaceElementStartOffset) {
          additionalEdits.add(TextUtil.createDeleteTextEdit(new Range(
              MiscUtil.offsetToPosition(copyThatCalledCompletionDoc, mergeRangeStartOffset),
              unresolvedTextEdit.getRange().getStart()
          )));
        }
        var prevEndOffset = editsToMergeRangesAsOffsets.first().range.first;
        for (var editToMerge : editsToMergeRangesAsOffsets) {
          builder.append(
              copyThatCalledCompletionDoc.getText(),
              prevEndOffset,
              editToMerge.range.first);

          prevEndOffset = editToMerge.range.second;

          builder.append(editToMerge.newText);
        }

        if (mergeRangeEndOffset < replaceElementEndOffset) {
          builder.append(copyThatCalledCompletionDoc.getText(),
              mergeRangeEndOffset,
              replaceElementEndOffset);
        } else if (replaceElementEndOffset != mergeRangeEndOffset) {
          additionalEdits.add(TextUtil.createDeleteTextEdit(new Range(
              unresolvedTextEdit.getRange().getEnd(),
              MiscUtil.offsetToPosition(copyThatCalledCompletionDoc, mergeRangeEndOffset)
          )));
        }

        unresolvedTextEdit.setNewText(builder.toString());
        unresolved.setAdditionalTextEdits(
            additionalEdits
        );
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

  @NotNull
  private TreeMap<@NotNull TextEditWithOffsets, @NotNull TextEdit> toMapOfOffsetsToTextEdits(
      @NotNull ArrayList<@NotNull TextEdit> list,
      @NotNull Document document) {
    var ans = new TreeMap<TextEditWithOffsets, TextEdit>();
    for (var edit : list) {
      var range = edit.getRange();
      ans.put(new TextEditWithOffsets(
          MiscUtil.positionToOffset(document, range.getStart()),
          MiscUtil.positionToOffset(document, range.getEnd())
          , edit.getNewText()), edit);
    }
    return ans;
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
                                                        @NotNull String prefix) {
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
              positionIDStarts.setCharacter(position.getCharacter() - prefix.length());
            }),
            position),
            lookupElement.getLookupString()
        )));

    resItem.setDetail(presentation.getTypeText());
    resItem.setTags(tagList);
    return resItem;
  }
}
