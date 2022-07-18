package org.rri.server.completions;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.rri.server.LspPath;
import org.rri.server.util.EditorUtil;
import org.rri.server.util.MiscUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service(Service.Level.PROJECT)
final public class MyCompletionsService implements Disposable {
  @NotNull
  private final Project project;
  private static final Logger LOG = Logger.getInstance(MyCompletionsService.class);

  public MyCompletionsService(@NotNull Project project) {
    this.project = project;
  }

  public CompletableFuture<Either<List<CompletionItem>, CompletionList>> getCompletionResults(
          LspPath path, Position position) {
    var app = ApplicationManager.getApplication();
    return CompletableFutures.computeAsync(
            AppExecutorUtil.getAppExecutorService(),
            (cancelChecker) -> {
              final Ref<Either<List<CompletionItem>, CompletionList>> ref = new Ref<>();
              // todo invokeAndWait is necessary for editor creation. We can create editor only inside EDT
              app.invokeAndWait(
                      // todo Add version that have return statement
                      () -> MiscUtil.withPsiFileInReadAction(
                              project,
                              path,
                              (psiFile) ->
                                      ref.set(createCompletionResults(psiFile, position, cancelChecker))),
                      app.getDefaultModalityState()
              );
              return ref.get();
            }
    );
  }

  // todo This method needs for editor creation
  @Override
  public void dispose() {
  }

  // todo This class uses ideas internal API
  @SuppressWarnings("UnstableApiUsage")
  public @NotNull Either<List<CompletionItem>, CompletionList> createCompletionResults(@NotNull PsiFile psiFile,
                                                                                       @NotNull Position position,
                                                                                       @NotNull CancelChecker cancelChecker) {
    Ref<List<LookupElement>> sortedLookupElementsRef = new Ref<>(new ArrayList<>());

    EditorUtil.withEditor(this, psiFile, position, (editor) -> {
      var ideaCompletionResults = new ArrayList<CompletionResult>();

      var process = new VoidCompletionProcess();

      var initContext = CompletionInitializationUtil.createCompletionInitializationContext(
              project,
              editor,
              editor.getCaretModel().getPrimaryCaret(),
              /* todo
                  This number equal completion requests count in same place.
                  It is used for change search scope in idea
              */ 1,
              CompletionType.BASIC);
      assert initContext != null;

      var topLevelOffsets =
              new OffsetsInFile(initContext.getFile(), initContext.getOffsetMap()).toTopLevelFile();
      PsiDocumentManager.getInstance(initContext.getProject()).commitAllDocuments();
      var hostCopyOffsets =
              insertDummyIdentifier(initContext, process, topLevelOffsets);
      assert hostCopyOffsets != null;

      OffsetsInFile finalOffsets = CompletionInitializationUtil.toInjectedIfAny(initContext.getFile(), hostCopyOffsets);
      CompletionParameters parameters = CompletionInitializationUtil.createCompletionParameters(
              initContext,
              process,
              finalOffsets);
      var arranger = new MyLookupArrangerImpl(parameters);
      var lookup = new LookupImpl(project, editor, arranger);

      var compService = CompletionService.getCompletionService();
      assert compService != null;

      compService.performCompletion(parameters,
              (result) -> {
                lookup.addItem(result.getLookupElement(),
                        new PlainPrefixMatcher("") /* todo Ref solutions authors chose this matcher */);
                ideaCompletionResults.add(result);
              });

      // todo This "if" comes from ref solution
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        ideaCompletionResults.forEach((it) -> {
          try {
            arranger.addElement(it);
          } catch (Exception ignored) {
          } // todo we just skip this element
        });
      } else {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
                () -> ideaCompletionResults.forEach((it) -> {
                  try {
                    arranger.addElement(it);
                  } catch (Exception ignored) {
                  } // todo we just skip this element
                }),
                "Sort completion elements", false, project);
      }
      sortedLookupElementsRef.set(arranger.arrangeItems(lookup, false).first);
    });

    cancelChecker.checkCanceled();
    var result = sortedLookupElementsRef.get().stream().map(
            (it) ->
            {
              var resItem = new CompletionItem();
              resItem.setLabel(it.getLookupString());
              return resItem;
            }
    ).collect(Collectors.toList());

    return Either.forLeft(result);
  }

  /* todo
      This process is needed for creation Completion Parameters and insertDummyIdentifier call.
      I didn't find an alternative way to find Indicator from project for completion
   */
  static private class VoidCompletionProcess extends AbstractProgressIndicatorExBase implements Disposable, CompletionProcess {
    @Override
    public boolean isAutopopupCompletion() {
      return false; // todo This is ref solution choice
    }

    // todo This lock from ref solution.
    private final Object myLock = ObjectUtils.sentinel("VoidCompletionProcess");

    @Override
    public void dispose() {
    }

    void registerChildDisposable(Supplier<Disposable> child) {
      synchronized (myLock) {
        // Idea developer says: "avoid registering stuff on an indicator being disposed concurrently"
        checkCanceled();
        Disposer.register(this, child.get());
      }
    }
  }

  /* todo
   This method is analogue for insertDummyIdentifier in CompletionInitializationUtil.java from idea 201.6668.113.
   There is CompletionProcessEx in ideas source code, that can't be reached publicly,
   but it uses only getHostOffsets and registerChildDisposable,
   that we can determine by ourself.
   So solution is copy that code with our replacement for getHostOffsets and registerChildDisposable calls
   Other private methods from CompletionInitializationUtil are copied below too.
  */
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
    return hostCopy.isValid() ? copyOffsets : null;
  }

  private static final Key<SoftReference<Pair<PsiFile, Document>>> FILE_COPY_KEY = Key.create("CompletionFileCopy");

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

  private static void syncAcceptSlashR(Document originalDocument, @NotNull Document documentCopy) {
    if (!(originalDocument instanceof DocumentImpl) || !(documentCopy instanceof DocumentImpl)) {
      return;
    }

    ((DocumentImpl) documentCopy).setAcceptSlashR(((DocumentImpl) originalDocument).acceptsSlashR());
  }

  private static boolean isCopyUpToDate(Document document, @NotNull PsiFile copyFile, @NotNull PsiFile originalFile) {
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

  private static @NonNls String fileInfo(@NotNull PsiFile file) {
    return file + " of " + file.getClass() +
            " in " + file.getViewProvider() + ", languages=" + file.getViewProvider().getLanguages() +
            ", physical=" + file.isPhysical();
  }

  // todo this assertion method is package-private in Ideas CompletionAssertions class
  private static void assertCorrectOriginalFile(@NonNls String prefix, PsiFile file, PsiFile copy) {
    if (copy.getOriginalFile() != file) {
      throw new AssertionError(prefix + " copied file doesn't have correct original: noOriginal=" + (copy.getOriginalFile() == copy) +
              "\n file " + fileInfo(file) +
              "\n copy " + fileInfo(copy));
    }
  }
}
