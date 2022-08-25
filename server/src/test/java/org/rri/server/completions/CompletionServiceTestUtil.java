package org.rri.server.completions;

import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.server.util.MiscUtil;

import java.util.ArrayList;

public class CompletionServiceTestUtil {
  public static CompletionItem removeResolveInfo(CompletionItem completionItem) {
    completionItem.setTextEdit(null);
    completionItem.setAdditionalTextEdits(null);
    completionItem.setData(null);
    return completionItem;
  }

  @SuppressWarnings("SameParameterValue")
  @NotNull
  public static CompletionItem createCompletionItem(@NotNull String label,
                                              @NotNull String labelDetail,
                                              @Nullable String detail,
                                              @NotNull ArrayList<@NotNull CompletionItemTag> completionItemTags,
                                              @NotNull String filterText,
                                              @Nullable CompletionItemKind kind) {
    return MiscUtil.with(new CompletionItem(), item -> {
      item.setLabel(label);
      item.setLabelDetails(MiscUtil.with(new CompletionItemLabelDetails(), completionItemLabelDetails -> completionItemLabelDetails.setDetail(labelDetail)));
      item.setDetail(detail);
      item.setTags(completionItemTags);
      item.setInsertTextFormat(InsertTextFormat.Snippet);
      item.setFilterText(filterText);
      item.setInsertTextMode(InsertTextMode.AsIs);
      item.setKind(kind);
    });
  }
}
