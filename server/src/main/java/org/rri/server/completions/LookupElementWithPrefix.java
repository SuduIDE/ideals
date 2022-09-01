package org.rri.server.completions;

import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.annotations.NotNull;

record LookupElementWithPrefix(@NotNull LookupElement lookupElement,
                               @NotNull String prefix) {
}
