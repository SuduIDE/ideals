package org.rri.server.completions;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.patterns.ElementPattern;
import com.intellij.util.Consumer;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.jetbrains.annotations.NotNull;

public class CompletionResultSetImpl extends CompletionResultSet {
  private final CompletionSorter sorter = CompletionService.getCompletionService().emptySorter();
  private Integer myLengthOfTextBeforePosition;
  private CompletionContributor contributor;
  private CompletionParameters parameters;
  private CompletionResultSetImpl original;
  private CancelChecker cancelToken;

  protected CompletionResultSetImpl(Consumer<? super CompletionResult> consumer,
                                    Integer myLengthOfTextBeforePosition,
                                    PrefixMatcher prefixMatcher,
                                    CompletionContributor contributor,
                                    CompletionParameters parameters,
                                    CompletionSorter sorter,
                                    CompletionResultSetImpl original,
                                    CancelChecker cancelToken) {
    super(prefixMatcher, consumer, contributor);
  }

  @Override
  public void addElement(@NotNull LookupElement element) {
//    if (!element.isValid()) {
//      // todo
//    }

    final var matched = CompletionResult.wrap(element, getPrefixMatcher(), sorter);
    if (matched != null) {
      passResult(matched);
    }
  }

  @Override
  public @NotNull CompletionResultSet withPrefixMatcher(@NotNull PrefixMatcher matcher) {
    return
            new CompletionResultSetImpl(
                    getConsumer(),
                    myLengthOfTextBeforePosition,
                    matcher,
                    contributor,
                    parameters,
                    sorter,
                    this,
                    cancelToken);
  }

  @Override
  public @NotNull CompletionResultSet withPrefixMatcher(@NotNull String prefix) {
    if (!prefix.isEmpty()) {
      // don't erase our prefix!
      // also, use `cloneWithPrefix` so our settings are preserved
      return withPrefixMatcher(getPrefixMatcher().cloneWithPrefix(prefix));
    }
    return this;
  }

  @Override
  public @NotNull CompletionResultSet withRelevanceSorter(@NotNull CompletionSorter sorter) {
    return new CompletionResultSetImpl(getConsumer(), myLengthOfTextBeforePosition, getPrefixMatcher(),
            contributor, parameters, sorter, this, cancelToken);
  }

  @Override
  public void addLookupAdvertisement(@NotNull @NlsContexts.PopupAdvertisement String text) {

  }

  @Override
  public @NotNull CompletionResultSet caseInsensitive() {
    return withPrefixMatcher(new CamelHumpMatcher(getPrefixMatcher().getPrefix(), false));
  }

  @Override
  public void restartCompletionOnPrefixChange(ElementPattern<String> prefixCondition) {

  }

  @Override
  public void restartCompletionWhenNothingMatches() {

  }
}
