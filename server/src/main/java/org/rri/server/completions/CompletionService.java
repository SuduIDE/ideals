package org.rri.server.completions;

import com.google.gson.JsonObject;
import com.intellij.codeInsight.completion.*;
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
import com.intellij.openapi.editor.EditorModificationUtilEx;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.server.LspPath;
import org.rri.server.util.EditorUtil;
import org.rri.server.util.MiscUtil;
import org.rri.server.util.TextUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Service(Service.Level.PROJECT)
final public class CompletionService implements Disposable {
  @NotNull
  private final Project project;
  private static final Logger LOG = Logger.getInstance(CompletionService.class);

  @NotNull
  private final List<@NotNull LookupElement> cachedLookupElements = new ArrayList<>();
  @Nullable
  private LspPath cachedPath = null;
  @Nullable
  private OffsetMap cachedOffsetMap = null;
  private int cachedCarretOffset = 0;
  @Nullable
  private LookupImpl cachedLookup = null;
  private int cachedStartOffset = 0;
  private int cachedResultIndex = 0;
  private String cachedPrefix = "";
  private Position cachedPosition = new Position();
  private String cachedText;
  private Language cachedLanguage;

  public CompletionService(@NotNull Project project) {
    this.project = project;
  }

  @NotNull
  public CompletableFuture<Either<List<CompletionItem>, CompletionList>> startCompletionCalculation(
      @NotNull LspPath path,
      @NotNull Position position) {
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
                  (psiFile) -> createCompletionResults(path, psiFile, position, cancelChecker)
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
  public @NotNull Either<List<CompletionItem>, CompletionList> createCompletionResults(@NotNull LspPath path,
                                                                                       @NotNull PsiFile psiFile,
                                                                                       @NotNull Position position,
                                                                                       @NotNull CancelChecker cancelChecker) {
    VoidCompletionProcess process = new VoidCompletionProcess();
    Ref<List<CompletionItem>> resultRef = new Ref<>();
    try {
      EditorUtil.withEditor(process, psiFile,
          position,
          (editor) -> {
            var ideaCompletionResults = new ArrayList<CompletionResult>();
            var initContext = CompletionInitializationUtil.createCompletionInitializationContext(
                project,
                editor,
                editor.getCaretModel().getPrimaryCaret(),
                1,
                CompletionType.BASIC);
            assert initContext != null;

            var topLevelOffsets =
                new OffsetsInFile(initContext.getFile(), initContext.getOffsetMap()).toTopLevelFile();
            PsiDocumentManager.getInstance(initContext.getProject()).commitAllDocuments();
            var hostCopyOffsets =
                insertDummyIdentifier(initContext, process, topLevelOffsets);

            OffsetsInFile finalOffsets = CompletionInitializationUtil.toInjectedIfAny(initContext.getFile(), hostCopyOffsets);
            CompletionParameters parameters = CompletionInitializationUtil.createCompletionParameters(
                initContext,
                process,
                finalOffsets);
            var arranger = new LookupArrangerImpl(parameters);
            var lookup = new LookupImpl(project, editor, arranger);

            var prefix = CompletionUtil.findReferencePrefix(parameters);
            assert prefix != null;

            var ideaCompService = com.intellij.codeInsight.completion.CompletionService.getCompletionService();
            assert ideaCompService != null;

            ideaCompService.performCompletion(parameters,
                (result) -> {
                  lookup.addItem(result.getLookupElement(),
                      result.getPrefixMatcher()
//                      new PlainPrefixMatcher(prefix) /* todo compare with another PrefixMatchers */
                  );
                  ideaCompletionResults.add(result);
                });

            ideaCompletionResults.forEach((it) -> {
              try {
                arranger.addElement(it);
              } catch (Exception ignored) {
              } // we just skip this element
            });
            List<LookupElement> sortedLookupElements = arranger.arrangeItems(lookup, false).first;

            int currentResultIndex;
            cancelChecker.checkCanceled();

            synchronized (cachedLookupElements) {
              cachedOffsetMap = initContext.getOffsetMap();
              cachedCarretOffset = editor.getCaretModel().getOffset();
              cachedPosition = position;
              cachedPath = path;
              cachedLookup = lookup;
              cachedText = editor.getDocument().getText();
              cachedLanguage = psiFile.getLanguage();
              currentResultIndex = ++cachedResultIndex;
              cachedStartOffset = editor.getCaretModel().getOffset() - prefix.length();
              cachedLookupElements.clear();
              cachedLookupElements.addAll(sortedLookupElements);
              cachedPrefix = prefix;
            }
            var result = sortedLookupElements.stream().map(
                (item) -> createLSPCompletionItem(item, position, prefix.length())).toList();

            for (int i = 0; i < result.size(); i++) {
              var completionItem = result.get(i);
              completionItem.setData(new Pair<>(currentResultIndex, i));
            }
            resultRef.set(result);
          }
      );
    } finally {
      Disposer.dispose(process);
    }

    return Either.forLeft(resultRef.get());
  }

  @SuppressWarnings({"unchecked", "UnstableApiUsage"})
  @NotNull
  public CompletableFuture<@NotNull CompletionItem> startCompletionResolve(@NotNull CompletionItem unresolved) {
    var app = ApplicationManager.getApplication();

    return CompletableFutures.computeAsync(
        AppExecutorUtil.getAppExecutorService(),
        (cancelChecker) -> {
          app.invokeAndWait(() -> {
            var insertedCopy = PsiFileFactory.getInstance(project).createFileFromText(
                "copy",
                cachedLanguage,
                cachedText,
                true,
                true,
                true);
            var oldCopy = (PsiFile) insertedCopy.copy();
            PsiFile cleanFile = (PsiFile) oldCopy.copy();
            app.runWriteAction(() -> WriteCommandAction.runWriteCommandAction(project,
                () -> {
                  var tempDisp = Disposer.newDisposable();
                  try {
                    EditorUtil.withEditor(tempDisp, cleanFile, cachedPosition, (editor) -> {
                      editor.getSelectionModel().setSelection(cachedStartOffset, cachedCarretOffset);
                      EditorModificationUtilEx.deleteSelectedText(editor);
                    });
                  } finally {
                    Disposer.dispose(tempDisp);
                  }
                }));

            var oldCopyDoc = MiscUtil.getDocument(oldCopy);
            var insertedCopyDoc = MiscUtil.getDocument(insertedCopy);
            var cleanFileDoc = MiscUtil.getDocument(cleanFile);

            JsonObject jsonObject = (JsonObject) unresolved.getData();
            var resultIndex = jsonObject.get("first").getAsInt();
            var lookupElementIndex = jsonObject.get("second").getAsInt();

            synchronized (cachedLookupElements) {
              if (resultIndex != cachedResultIndex) {
                return;
              }

              var cachedLookupElement = cachedLookupElements.get(lookupElementIndex);

              app.runReadAction(() -> {
                var tempDisposable = Disposer.newDisposable();

                try {
                  var process = new VoidCompletionProcess();
                  EditorUtil.withEditor(tempDisposable, insertedCopy, cachedPosition, editor -> {
                    var initContext = CompletionInitializationUtil.createCompletionInitializationContext(
                        project,
                        editor,
                        editor.getCaretModel().getPrimaryCaret(),
                        1,
                        CompletionType.BASIC);
                    assert initContext != null;

                    var topLevelOffsets =
                        new OffsetsInFile(initContext.getFile(), initContext.getOffsetMap()).toTopLevelFile();
                    PsiDocumentManager.getInstance(initContext.getProject()).commitAllDocuments();
                    var hostCopyOffsets =
                        insertDummyIdentifier(initContext, process, topLevelOffsets);

                    OffsetsInFile finalOffsets = CompletionInitializationUtil.toInjectedIfAny(initContext.getFile(), hostCopyOffsets);
                    CompletionParameters parameters = CompletionInitializationUtil.createCompletionParameters(
                        initContext,
                        process,
                        finalOffsets);
                    var arranger = new LookupArrangerImpl(parameters);
                    var lookup = new LookupImpl(project, editor, arranger);

                    var prefix = CompletionUtil.findReferencePrefix(parameters);
                    assert prefix != null;

                    lookup.addItem(cachedLookupElement, new CamelHumpMatcher(prefix));
                    arranger.addElement(cachedLookupElement, new LookupElementPresentation());
                    lookup.finishLookup('\n', cachedLookupElement);

                    var currentOffset = editor.getCaretModel().getOffset();
                    ApplicationManager.getApplication().runWriteAction(() -> {
                      WriteCommandAction.runWriteCommandAction(project, () -> {
                        var context =
                            CompletionUtil.createInsertionContext(
                                cachedLookupElements,
                                cachedLookupElement,
                                '\n',
                                editor,
                                insertedCopy,
                                currentOffset,
                                CompletionUtil.calcIdEndOffset(
                                    initContext.getOffsetMap(),
                                    editor,
                                    currentOffset),
                                initContext.getOffsetMap());

                        cachedLookupElement.handleInsert(context);
                        LOG.warn(insertedCopy.getText());

                      });
                    });

                  });
                  assert insertedCopyDoc != null;
                  assert oldCopyDoc != null;
                  assert cleanFileDoc != null;
                  var diff = TextUtil.textEditFromDocs(cleanFileDoc, insertedCopyDoc);
                  LOG.warn(diff.toString());
                  Pair<TextEdit, List<TextEdit>> mainAndAdditionalTextEdits =
                      reformatTextEditsAfterInsert(diff,
                          MiscUtil.offsetToPosition(oldCopyDoc, cachedStartOffset),
                          MiscUtil.offsetToPosition(oldCopyDoc, cachedCarretOffset));
                  var mainEdit = mainAndAdditionalTextEdits.first;
                  var otherEdits = mainAndAdditionalTextEdits.second;
                  unresolved.getTextEdit().getLeft().setNewText(mainEdit.getNewText());
                  unresolved.setAdditionalTextEdits(otherEdits);
                } finally {
                  Disposer.dispose(tempDisposable);
                }
              });
            }
            cancelChecker.checkCanceled();

          });
          return unresolved;
        });

  }


  @NotNull
  private Pair<@NotNull TextEdit, @NotNull List<@NotNull TextEdit>> reformatTextEditsAfterInsert(
      @NotNull List<TextEdit> diff,
      @NotNull Position startPos,
      @NotNull Position endPos
  ) {

    var mainTextEdit = new TextEdit();
    assert startPos.getLine() == endPos.getLine();
    Range mainRange = new Range(startPos, endPos);
    for (TextEdit textEdit : diff) {
      if (rangesCollision(mainRange, textEdit)) {
        mainTextEdit = textEdit;
        break;
      }
    }
    final TextEdit finalMainTextEdit = mainTextEdit;
    return new Pair<>(mainTextEdit,
        diff.stream().filter((textEdit -> !textEdit.equals(finalMainTextEdit))).toList());
  }

  private boolean rangesCollision(Range mainRange, TextEdit edit) {
    var editRange = edit.getRange();
    if (mainRange.getStart().getLine() != editRange.getStart().getLine() ||
        mainRange.getStart().getLine() != editRange.getEnd().getLine()) {
      return false;
    }
    // todo normal language
    if (!editRange.getStart().equals(editRange.getEnd())) {
      return false;
    }
    return !(mainRange.getEnd().getCharacter() <= editRange.getStart().getCharacter() ||
        mainRange.getStart().getCharacter() >= editRange.getStart().getCharacter() + edit.getNewText().length());
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

    resItem.setLabel(presentation.getItemText());
    resItem.setLabelDetails(lDetails);

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

  /* todo
      Find an alternative way to find Indicator from project for completion
      This process is needed for creation Completion Parameters and insertDummyIdentifier call.
   */
  static private class VoidCompletionProcess extends AbstractProgressIndicatorExBase implements Disposable, CompletionProcess {
    @Override
    public boolean isAutopopupCompletion() {
      return false;
    }

    // todo check that we don't need this lock
    @NotNull
    private final Object myLock = ObjectUtils.sentinel("VoidCompletionProcess");

    @Override
    public void dispose() {
    }

    void registerChildDisposable(@NotNull Supplier<Disposable> child) {
      synchronized (myLock) {
        // Idea developer says: "avoid registering stuff on an indicator being disposed concurrently"
        checkCanceled();
        Disposer.register(this, child.get());
      }
    }
  }

  /*
   This method is analogue for insertDummyIdentifier in CompletionInitializationUtil.java from idea 201.6668.113.
   There is CompletionProcessEx in ideas source code, that can't be reached publicly,
   but it uses only getHostOffsets and registerChildDisposable, that we can determine by ourselves.
   So solution is to copy that code with our replacement for getHostOffsets and registerChildDisposable calls.
   Other private methods from CompletionInitializationUtil are copied below too.
  */
  @NotNull
  private OffsetsInFile insertDummyIdentifier(
      @NotNull CompletionInitializationContext initContext,
      @NotNull VoidCompletionProcess indicator,
      @NotNull OffsetsInFile topLevelOffsets) {
    var hostEditor = InjectedLanguageEditorUtil.getTopLevelEditor(initContext.getEditor());
    var hostMap = topLevelOffsets.getOffsets();
    boolean forbidCaching = false;

    var hostCopy = obtainFileCopy(topLevelOffsets.getFile(), forbidCaching);
    var copyDocument = hostCopy.getViewProvider().getDocument();
    var dummyIdentifier = initContext.getDummyIdentifier();
    var startOffset = hostMap.getOffset(CompletionInitializationContext.START_OFFSET);
    var endOffset = hostMap.getOffset(CompletionInitializationContext.SELECTION_END_OFFSET);

    indicator.registerChildDisposable(
        () -> new OffsetTranslator(
            hostEditor.getDocument(),
            initContext.getFile(),
            copyDocument,
            startOffset,
            endOffset,
            dummyIdentifier)
    );

    var copyOffsets = topLevelOffsets.replaceInCopy(
        hostCopy, startOffset, endOffset, dummyIdentifier).get();
    if (!hostCopy.isValid()) {
      throw new RuntimeException("PsiFile copy is not valid anymore");
    }
    return copyOffsets;
  }

  @NotNull
  private static final Key<SoftReference<Pair<PsiFile, Document>>> FILE_COPY_KEY = Key.create("CompletionFileCopy");

  @NotNull
  private static PsiFile obtainFileCopy(@NotNull PsiFile file,
                                        boolean forbidCaching) {
    final VirtualFile virtualFile = file.getVirtualFile();
    boolean mayCacheCopy = !forbidCaching && file.isPhysical() &&
        // Idea developer: "we don't want to cache code fragment copies even if they appear to be physical"
        virtualFile != null && virtualFile.isInLocalFileSystem();
    if (mayCacheCopy) {
      final Pair<PsiFile, Document> cached = SoftReference.dereference(file.getUserData(FILE_COPY_KEY));
      if (cached != null && isCopyUpToDate(cached.second, cached.first, file)) {
        PsiFile copy = cached.first;
        assertCorrectOriginalFile("Cached", file, copy);
        return copy;
      }
    }

    final PsiFile copy = (PsiFile) file.copy();
    if (copy.isPhysical() || copy.getViewProvider().isEventSystemEnabled()) {
      LOG.error("File copy should be non-physical and non-event-system-enabled! Language=" +
          file.getLanguage() +
          "; file=" +
          file +
          " of " +
          file.getClass());
    }
    assertCorrectOriginalFile("New", file, copy);

    if (mayCacheCopy) {
      final Document document = copy.getViewProvider().getDocument();
      assert document != null;
      syncAcceptSlashR(file.getViewProvider().getDocument(), document);
      file.putUserData(FILE_COPY_KEY, new SoftReference<>(Pair.create(copy, document)));
    }
    return copy;
  }

  private static void syncAcceptSlashR(@Nullable Document originalDocument, @NotNull Document documentCopy) {
    if (!(originalDocument instanceof DocumentImpl) || !(documentCopy instanceof DocumentImpl)) {
      return;
    }

    ((DocumentImpl) documentCopy).setAcceptSlashR(((DocumentImpl) originalDocument).acceptsSlashR());
  }

  private static boolean isCopyUpToDate(Document document,
                                        @NotNull PsiFile copyFile,
                                        @NotNull PsiFile originalFile) {
    if (!copyFile.getClass().equals(originalFile.getClass()) ||
        !copyFile.isValid() ||
        !copyFile.getName().equals(originalFile.getName())) {
      return false;
    }
    /*
     Idea developers:
     the psi file cache might have been cleared by some external activity,
     in which case PSI-document sync may stop working
     */
    PsiFile current = PsiDocumentManager.getInstance(copyFile.getProject()).getPsiFile(document);
    return current != null && current.getViewProvider().getPsi(copyFile.getLanguage()) == copyFile;
  }

  @NotNull
  private static @NonNls String fileInfo(@NotNull PsiFile file) {
    return file + " of " + file.getClass() +
        " in " + file.getViewProvider() + ", languages=" + file.getViewProvider().getLanguages() +
        ", physical=" + file.isPhysical();
  }

  // this assertion method is copied from package-private method in CompletionAssertions class
  private static void assertCorrectOriginalFile(@NonNls String prefix,
                                                @NotNull PsiFile file,
                                                @NotNull PsiFile copy) {
    if (copy.getOriginalFile() != file) {
      throw new AssertionError(prefix + " copied file doesn't have correct original: noOriginal=" + (copy.getOriginalFile() == copy) +
          "\n file " + fileInfo(file) +
          "\n copy " + fileInfo(copy));
    }
  }
}
