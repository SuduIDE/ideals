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

public class MyLookupArrangerImpl extends LookupArranger {
  @NotNull
  CompletionParameters parameters;
  @NotNull
  CompletionLocation location;
  public MyLookupArrangerImpl(@NotNull CompletionParameters parameters) {
    this.parameters = parameters;
    this.location = new CompletionLocation(parameters);
  }
  private final ArrayList<LookupElement> items = new ArrayList<>();

  public void addElement(@NotNull CompletionResult completionItem) {
    var presentation = new LookupElementPresentation();
    ReadAction.run(() -> completionItem.getLookupElement().renderElement(presentation));

    items.add(completionItem.getLookupElement());
    super.addElement(completionItem.getLookupElement(), presentation);
  }

  @Override
  public Pair<List<LookupElement>, Integer> arrangeItems(@NotNull Lookup lookup, boolean onExplicitAction) {

    //val toSelect = getItemToSelect(lookupImpl, listModel, onExplicitAction, relevantSelection) <- from ref solution comment
    //LOG.assertTrue(toSelect >= 0) <- from ref solution comment
    var toSelect = 0;
    return new Pair<>(items, toSelect);
  }

  @Override
  public LookupArranger createEmptyCopy() {
    return new MyLookupArrangerImpl(parameters);
  }

}
