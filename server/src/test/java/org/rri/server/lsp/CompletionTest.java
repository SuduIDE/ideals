package org.rri.server.lsp;

import org.eclipse.lsp4j.*;
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
  final Position completionInvokePosition = new Position();
  final int completionInvokeLine = 8;
  final int completionInvokeCharacter = 7;

  private final String LABEL = "completionVariant";
  private final String INSERT_TEXT = LABEL;
  private final String DETAIL = "void";
  private final String LABEL_DETAIL_DESCRIPTION = DETAIL;
  private final ArrayList<CompletionItemTag> TAGS = new ArrayList<>();

  final Set<CompletionItem> CORRECT_COMPLETION_ITEMS_SET = new HashSet<>();

  @Override
  protected void setUp() throws Exception {
    System.setProperty("idea.log.debug.categories", "#org.rri");
    completionInvokePosition.setLine(completionInvokeLine);
    completionInvokePosition.setCharacter(completionInvokeCharacter);

    addToSet("()");
    addToSet("(int x)");

    super.setUp();
  }

  private void addToSet(@NotNull String completionItemLabelDetail) {
    CORRECT_COMPLETION_ITEMS_SET.add(
            MiscUtil.with(
                    new CompletionItem(),
                    completionItem -> {
                      completionItem.setLabel(LABEL);
                      completionItem.setInsertText(INSERT_TEXT);
                      completionItem.setLabelDetails(MiscUtil.with(new CompletionItemLabelDetails(), completionItemLabelDetails -> {
                        completionItemLabelDetails.setDetail(completionItemLabelDetail);
                        completionItemLabelDetails.setDescription(LABEL_DETAIL_DESCRIPTION);
                      }));
                      completionItem.setDetail(DETAIL);
                      completionItem.setTags(TAGS);
                    }
            ));

  }

  @Override
  protected String getProjectRelativePath() {
    return "completion-project";
  }

  @Test
  public void completion() {
    Assertions.assertDoesNotThrow(() -> {
      var completionRes =
              TestUtil.getNonBlockingEdt(server().getTextDocumentService().completion(completionParams()), 3000
              );
      List<CompletionItem> completionItemList;
      if (completionRes.isRight()) {
        completionItemList = completionRes.getRight().getItems();
      } else {
        Assert.assertTrue(completionRes.isLeft());
        completionItemList = completionRes.getLeft();
      }
      var completionItemSet = new HashSet<>(completionItemList);
      Assert.assertEquals(completionItemSet.size(), completionItemList.size());
      Assert.assertEquals(completionItemSet, CORRECT_COMPLETION_ITEMS_SET);
    });
  }

  @NotNull
  private CompletionParams completionParams() {
    final var filePath = LspPath.fromLocalPath(getProjectPath().resolve("src/CompletionExampleTest.java"));

    var params = new CompletionParams();

    params.setTextDocument(
            MiscUtil.with(new TextDocumentIdentifier(),
                    documentIdentifier -> documentIdentifier.setUri(filePath.toLspUri())));
    params.setPosition(completionInvokePosition);
    return params;
  }
}
