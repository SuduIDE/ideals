package org.rri.server.completions;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.LookupArranger;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.rri.server.util.EditorUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

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
    AtomicReference<List<LookupElement>> sortedRef = new AtomicReference<>();
    EditorUtil.withEditor(this, psiFile, position, (editor) -> {
      assert psiFile != null;
      var language = Objects.requireNonNull(PsiUtilBase.getLanguageInEditor(editor, project));
      var contributors = CompletionContributor.forLanguageHonorDumbness(
              language, project
      );
      var offset = editor.getCaretModel().getOffset();
      var context =
              new CompletionInitializationContext(
                      editor,
                      editor.getCaretModel().getCurrentCaret(),
                      language,
                      psiFile,
                      CompletionType.BASIC,
                      0);
      var element = context.getFile().findElementAt(offset);
//              Objects.requireNonNull(psiFile.findReferenceAt(offset)).resolve();



      var arranger = new LookupArranger.DefaultArranger();
      var lookup = new LookupImpl(project, editor, arranger);
      var completionResults = new ArrayList<CompletionResult>();
      var params = new CompletionParameters(
              Objects.requireNonNull(element),
              psiFile.getOriginalFile(),
              CompletionType.BASIC,
              offset,
              0,
              editor,
              () -> false);

      var matcher = new CamelHumpMatcher("", false);
      var sorter = CompletionService.getCompletionService().emptySorter();
      var lookupSet = new HashSet<LookupElement>();

      for (var contributor : contributors) {
        contributor.beforeCompletion(context);
        cancelChecker.checkCanceled();
        // todo completion phase / base completion service
//                CompletionUtil.findIdentifierPrefix(
//                        element,
//                        offset,
//                        null,
//                        null);
//        var arranger = MyCompletionLookupArranger(params, CompletionLocation(params))

        var resultSet = new CompletionResultSetImpl(
                completionResult -> {
                  if (lookupSet.add(completionResult.getLookupElement())) {
                    lookup.addItem(completionResult.getLookupElement(), new CamelHumpMatcher(""));

                    var el = completionResult.getLookupElement();

                    completionResults.add(completionResult);
                  }
                },
                params.getOffset(),
                matcher,
                contributor,
                params,
                sorter,
                null,
                cancelChecker
        );

        contributor.fillCompletionVariants(params, resultSet);
//        if (resultSet.isStopped()) {
//          return;
//        }
      }
      ProgressManager.getInstance().runProcessWithProgressSynchronously(
              () -> completionResults.forEach(
                it -> arranger.addElement(it.getLookupElement(), null)
              ), "Sort completion elements", false, project);
      var sortedLookUpElements = arranger.arrangeItems(lookup, false).getFirst();
      sortedRef.set(sortedLookUpElements);

//            var first = contributors.get(0);
//            first.beforeCompletion();
    });

    var result = sortedRef.get().stream()
            .map(it -> from(it, false));
    return Either.forRight(new CompletionList(false, result.collect(Collectors.toList())));
  }

  private CompletionItem from(LookupElement lookup, boolean snippetSupport) {
    var psi = lookup.getPsiElement();

//todo
//    if (lookup.`object` is String || lookup.`object` is KeywordLookupObject) {
//      return KtKeywordCompletionDecorator(lookup)
//    }

    // handle generated properties and language builtin methods
    assert psi != null;

//    var decorator = fromSyntheticLookupElement(lookup);
    var ans = new CompletionItem();
    ans.setLabel(psi.getText());
    LOG.info("RAMAZAN FLEX " + psi.getClass().toString());
    return ans;
//    if (decorator == null) {
//      decorator = when (psi) {
//        is PsiMethod -> MethodCompletionDecorator(lookup, psi)
//        is PsiClass -> ClassCompletionDecorator(lookup, psi)
//        is PsiField -> FieldCompletionDecorator(lookup, psi)
//        is PsiVariable -> VariableCompletionDecorator(lookup, psi)
//        is PsiPackage -> PackageCompletionDecorator(lookup, psi)
//
//        is KtElement -> fromKotlin(lookup)
//                    else -> null
//      }
//    }
//    decorator?.clientSupportsSnippets = snippetSupport
//    return decorator
  }

  @Override
  public void dispose() {
  }
}
