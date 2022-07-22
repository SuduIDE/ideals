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
  static private final Position COMPLETION_INVOKE_POSITION = new Position();
  static private final int COMPLETION_INVOKE_LINE = 8;
  static private final int COMPLETION_INVOKE_CHARACTER = 7;

  static private final String LABEL = "completionVariant";
  static private final String INSERT_TEXT = LABEL;
  static private final String DETAIL = "void";
  static private final String LABEL_DETAIL_DESCRIPTION = DETAIL;
  static private final ArrayList<CompletionItemTag> TAGS = new ArrayList<>();

  static private final Set<CompletionItem> CORRECT_COMPLETION_ITEMS_SET = new HashSet<>();

  @Override
  protected void setUp() throws Exception {
    System.setProperty("idea.log.debug.categories", "#org.rri");
    COMPLETION_INVOKE_POSITION.setLine(COMPLETION_INVOKE_LINE);
    COMPLETION_INVOKE_POSITION.setCharacter(COMPLETION_INVOKE_CHARACTER);

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
      Assert.assertEquals(CORRECT_COMPLETION_ITEMS_SET, new HashSet<>(completionItemList));
    });
  }

  @NotNull
  private CompletionParams completionParams() {
    final var filePath = LspPath.fromLocalPath(getProjectPath().resolve("src/CompletionExampleTest.java"));

    var params = new CompletionParams();

    params.setTextDocument(
            MiscUtil.with(new TextDocumentIdentifier(),
                    documentIdentifier -> documentIdentifier.setUri(filePath.toLspUri())));
    params.setPosition(COMPLETION_INVOKE_POSITION);
    return params;
  }
}
