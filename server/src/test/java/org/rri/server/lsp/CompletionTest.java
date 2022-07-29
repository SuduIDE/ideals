package org.rri.server.lsp;

import com.intellij.openapi.util.Ref;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.rri.server.LspPath;
import org.rri.server.TestUtil;
import org.rri.server.util.MiscUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CompletionTest extends LspServerTestBase {

  @Override
  protected String getProjectRelativePath() {
    return "completion/completion-project";
  }

  @Test
  public void completion() {
    final String label = "completionVariant";
    final Position completionInvokePosition = new Position(8, 7);

    final Set<CompletionItem> expected = Set.of(
        newCompletionItem(label, "int", "()"),
        newCompletionItem(label, "void", "(int x)")
    );

    final var filePath = LspPath.fromLocalPath(getProjectPath().resolve("src/CompletionExampleTest.java"));

    var params = new CompletionParams();

    params.setTextDocument(TestUtil.getDocumentIdentifier(filePath));
    params.setPosition(completionInvokePosition);

    Ref<Either<List<CompletionItem>, CompletionList>> completionResRef = new Ref<>();

    Assertions.assertDoesNotThrow(() -> completionResRef.set(
        TestUtil.getNonBlockingEdt(server().getTextDocumentService().completion(params), 3000)));

    Assert.assertEquals(expected, new HashSet<>(extractItemList(completionResRef.get())));
  }

  @NotNull
  private static List<CompletionItem> extractItemList(@NotNull Either<List<CompletionItem>, CompletionList> completionResult) {
    List<CompletionItem> completionItemList;
    if (completionResult.isRight()) {
      completionItemList = completionResult.getRight().getItems();
    } else {
      Assert.assertTrue(completionResult.isLeft());
      completionItemList = completionResult.getLeft();
    }
    return completionItemList;
  }

  @SuppressWarnings("SameParameterValue")
  @NotNull
  private static CompletionItem newCompletionItem(@NotNull String label, @NotNull String detail, @NotNull String completionItemLabelDetail) {
    return MiscUtil.with(
        new CompletionItem(),
        item -> {
          item.setLabel(label);
          item.setInsertText(label);
          item.setLabelDetails(MiscUtil.with(new CompletionItemLabelDetails(),
              labelDetails -> labelDetails.setDetail(completionItemLabelDetail)));
          item.setDetail(detail);
          item.setTags(new ArrayList<>());
        }
    );
  }
}
