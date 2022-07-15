package org.rri.server.completions;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
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
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.rri.server.util.EditorUtil;

import java.util.ArrayList;
import java.util.List;
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

  public @NotNull Either<List<CompletionItem>, CompletionList> launchCompletions(@NotNull PsiFile psiFile,
                                                                                 @NotNull Position position,
                                                                                 @NotNull CancelChecker cancelChecker) {
    Ref<List<LookupElement>> sortedLookupElementsRef = new Ref<>(new ArrayList<>());

    EditorUtil.withEditor(this, psiFile, position, (editor) -> {
      var completionResults = new ArrayList<CompletionResult>();

      var process = new VoidCompletionProcess();

      var initContext = CompletionInitializationUtil.createCompletionInitializationContext(
              project,
              editor,
              editor.getCaretModel().getPrimaryCaret(),
              0 /* i dont know purpose of this number */,
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
                        new CamelHumpMatcher("") /* ref solutions authors chose this matcher */);
                completionResults.add(result);
              });

      Ref<List<LookupElement>> sorted = new Ref<>(new ArrayList<>());

      // this "if" comes from Idea and reference solution
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        completionResults.forEach((it) -> {
          try {
            arranger.addElement(it);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
      } else {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
                () -> completionResults.forEach((it) -> {
                  try {
                    arranger.addElement(it);
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                }),
                "Sort completion elements", false, project);
      }

      sorted.set(arranger.arrangeItems(lookup, false).first);
      sortedLookupElementsRef.set(sorted.get());
    });

    cancelChecker.isCanceled();
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

  static private class VoidCompletionProcess extends AbstractProgressIndicatorExBase implements Disposable, CompletionProcess {
    @Override
    public boolean isAutopopupCompletion() {
      return false; // ref solution choice
    }

    // this lock from ref solution. I didnt found an alternative way to find Indicator from project for completion
    private final String myLock = "VoidCompletionProcess";

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

  private OffsetsInFile insertDummyIdentifier(
          @NotNull CompletionInitializationContext initContext,
          @NotNull VoidCompletionProcess indicator,
          @NotNull OffsetsInFile topLevelOffsets) {
    var hostEditor = InjectedLanguageEditorUtil.getTopLevelEditor(initContext.getEditor());
    var hostMap = topLevelOffsets.getOffsets();
    var hostCopy = obtainFileCopy(topLevelOffsets.getFile(), false);
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
            hostCopy, startOffset, endOffset, dummyIdentifier
    ).get();
    return hostCopy.isValid() ? copyOffsets : null;
  }

  private static final Key<SoftReference<Pair<PsiFile, Document>>> FILE_COPY_KEY = Key.create("CompletionFileCopy");

  private static PsiFile obtainFileCopy(@NotNull PsiFile file, boolean forbidCaching) {
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
    // assertion from idea source
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
    // Idea developer:
    // the psi file cache might have been cleared by some external activity,
    // in which case PSI-document sync may stop working
    PsiFile current = PsiDocumentManager.getInstance(copyFile.getProject()).getPsiFile(document);
    return current != null && current.getViewProvider().getPsi(copyFile.getLanguage()) == copyFile;
  }

  private static void assertCorrectOriginalFile(@NonNls String prefix, PsiFile file, PsiFile copy) {
    if (copy.getOriginalFile() != file) {
      throw new AssertionError(prefix + " copied file doesn't have correct original: noOriginal=" + (copy.getOriginalFile() == copy) +
              "\n file " + fileInfo(file) +
              "\n copy " + fileInfo(copy));
    }
  }

  private static @NonNls String fileInfo(@NotNull PsiFile file) {
    return file + " of " + file.getClass() +
            " in " + file.getViewProvider() + ", languages=" + file.getViewProvider().getLanguages() +
            ", physical=" + file.isPhysical();
  }

  @Override
  public void dispose() {
  }
}
