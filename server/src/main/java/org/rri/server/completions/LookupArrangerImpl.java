package org.rri.server.completions;

import com.intellij.codeInsight.completion.CompletionLocation;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResult;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupArranger;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/* todo
  This LookupArranger implementation doesn't use methods from parent class,
  its just simple arranger that can contain lookupElements from CompletionResults
 */
class LookupArrangerImpl extends LookupArranger {
  @NotNull
  CompletionParameters parameters;
  @NotNull
  CompletionLocation location;
  @NotNull
  private final ArrayList<LookupElement> items = new ArrayList<>();


  public LookupArrangerImpl(@NotNull CompletionParameters parameters) {
    this.parameters = parameters;
    this.location = new CompletionLocation(parameters);
  }

  public void addElement(@NotNull CompletionResult completionItem) {
    var presentation = new LookupElementPresentation();
    ReadAction.run(() -> completionItem.getLookupElement().renderElement(presentation));

    items.add(completionItem.getLookupElement());
    super.addElement(completionItem.getLookupElement(), presentation);
  }

  @Override
  @NotNull
  public Pair<List<LookupElement>, Integer> arrangeItems(@NotNull Lookup lookup, boolean onExplicitAction) {
    var toSelect = 0;
    return new Pair<>(items, toSelect);
  }

  @Override
  @NotNull
  public LookupArranger createEmptyCopy() {
    return new LookupArrangerImpl(parameters);
  }

}
