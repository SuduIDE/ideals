package org.rri.server.completions;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupArranger;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.rri.server.util.EditorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service(Service.Level.PROJECT)
final public class MyCompletionsService implements Disposable {
  private final Project project;
  private static final Logger LOG = Logger.getInstance(MyCompletionsService.class);
  private final AtomicReference<ArrayList<CompletionItem>> cachedPreviousCompletion
          = new AtomicReference<>(new ArrayList<>());
  private final AtomicLong completionResultIndex = new AtomicLong(0);

  public MyCompletionsService(Project project) {
    this.project = project;
  }

  public Either<List<CompletionItem>, CompletionList> launchCompletions(PsiFile psiFile, Position position, CancelChecker cancelChecker) {
    completionResultIndex.incrementAndGet();
    var compService = CompletionService.getCompletionService();
    assert compService != null;

//    compService.performCompletion(null, (result) -> {});
    EditorUtil.withEditor(this, psiFile, position, (editor) -> {
      var completionResults = new ArrayList<CompletionResult>();
      var arranger = new LookupArranger.DefaultArranger();
      var lookup = new LookupImpl(project, editor, arranger);

      var initContext = CompletionInitializationUtil.createCompletionInitializationContext(
              project, editor, editor.getCaretModel().getPrimaryCaret(), 0, CompletionType.BASIC);
      assert initContext != null;

//      CompletionProgressIndicator indicator = todo ???;
//      assert indicator != null;

      PsiDocumentManager.getInstance(initContext.getProject()).commitAllDocuments();
      var hostCopyOffsets =  CompletionInitializationUtil.insertDummyIdentifier(initContext, indicator).get();

      OffsetsInFile finalOffsets = CompletionInitializationUtil.toInjectedIfAny(initContext.getFile(), hostCopyOffsets);
      CompletionParameters parameters = CompletionInitializationUtil.createCompletionParameters(initContext, null, finalOffsets);

//      compService.performCompletion(null,
//              (result) -> {
//                lookup.addItem(result.getLookupElement(), new CamelHumpMatcher(""));
//
//                completionResults.add(result);
//              });
    });
    return Either.forLeft(new ArrayList<>());
  }

  @Override
  public void dispose() {
  }
}
