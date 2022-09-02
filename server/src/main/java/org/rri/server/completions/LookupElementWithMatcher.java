package org.rri.server.completions;

import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.annotations.NotNull;

record LookupElementWithMatcher(@NotNull LookupElement lookupElement,
                                @NotNull PrefixMatcher prefixMatcher) {
}
