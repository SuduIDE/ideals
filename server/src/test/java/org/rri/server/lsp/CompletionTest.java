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
import java.util.function.Consumer;

public class CompletionTest extends LspServerTestBase {
  @Override
  protected void setUp() throws Exception {
    System.setProperty("idea.log.debug.categories", "#org.rri");
    super.setUp();
  }

  @Override
  protected String getProjectRelativePath() {
    return "completion-project";
  }

  @Test
  public void completion() {
    final String LABEL = "completionVariant";
    final String DETAIL = "void";
    final Position COMPLETION_INVOKE_POSITION = new Position(8, 7);

    final ArrayList<CompletionItemTag> TAGS = new ArrayList<>();

    final Set<CompletionItem> CORRECT_COMPLETION_ITEMS_SET = new HashSet<>();
    Consumer<String> addToSet = (@NotNull String completionItemLabelDetail) -> CORRECT_COMPLETION_ITEMS_SET.add(
        MiscUtil.with(
            new CompletionItem(),
            completionItem -> {
              completionItem.setLabel(LABEL);
              completionItem.setInsertText(LABEL);
              completionItem.setLabelDetails(MiscUtil.with(new CompletionItemLabelDetails(),
                  completionItemLabelDetails -> {
                    completionItemLabelDetails.setDetail(completionItemLabelDetail);
                    completionItemLabelDetails.setDescription(DETAIL);
                  }));
              completionItem.setDetail(DETAIL);
              completionItem.setTags(TAGS);
            }
        ));

    addToSet.accept("()");
    addToSet.accept("(int x)");

    final var filePath = LspPath.fromLocalPath(getProjectPath().resolve("src/CompletionExampleTest.java"));

    var params = new CompletionParams();

    params.setTextDocument(
        MiscUtil.with(new TextDocumentIdentifier(),
            documentIdentifier -> documentIdentifier.setUri(filePath.toLspUri())));
    params.setPosition(COMPLETION_INVOKE_POSITION);

    Ref<Either<List<CompletionItem>, CompletionList>> completionResRef = new Ref<>();

    Assertions.assertDoesNotThrow(() -> completionResRef.set(
        TestUtil.getNonBlockingEdt(server().getTextDocumentService().completion(params), 3000)));

    var completionRes = completionResRef.get();
    List<CompletionItem> completionItemList;
    if (completionRes.isRight()) {
      completionItemList = completionRes.getRight().getItems();
    } else {
      Assert.assertTrue(completionRes.isLeft());
      completionItemList = completionRes.getLeft();
    }
    Assert.assertEquals(CORRECT_COMPLETION_ITEMS_SET, new HashSet<>(completionItemList));
  }
}
