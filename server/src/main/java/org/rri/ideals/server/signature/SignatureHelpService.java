package org.rri.ideals.server.signature;

import com.intellij.codeInsight.hint.ParameterInfoControllerBase;
import com.intellij.codeInsight.hint.ShowParameterInfoContext;
import com.intellij.codeInsight.hint.ShowParameterInfoHandler;
import com.intellij.lang.Language;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.lang.parameterInfo.ParameterInfoUIContextEx;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.JBColor;
import com.intellij.util.Function;
import com.intellij.util.indexing.DumbModeAccessType;
import io.github.furstenheim.CopyDown;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.util.EditorUtil;
import org.rri.ideals.server.util.MiscUtil;

import java.awt.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

@Service(Service.Level.PROJECT)
final public class SignatureHelpService implements Disposable {
  private static final Logger LOG = Logger.getInstance(SignatureHelpService.class);
  @NotNull
  private final Project project;

  public SignatureHelpService(@NotNull Project project) {
    this.project = project;
  }

  @Override
  public void dispose() {
  }

  @Nullable
  public SignatureHelp computeSignatureHelp(@NotNull LspPath path,
                                            @NotNull Position position,
                                            @NotNull CancelChecker cancelChecker) {
    LOG.info("start signature help");
    try {
      var virtualFile = path.findVirtualFile();
      if (virtualFile == null) {
        LOG.warn("file not found: " + path);
        return null;
      }
      final var psiFile = MiscUtil.resolvePsiFile(project, path);
      assert psiFile != null;
      final var doc = ReadAction.compute(() -> MiscUtil.getDocument(psiFile));
      assert doc != null;
      final var offset = MiscUtil.positionToOffset(doc, position);

      var htmlToMarkdownConverter = new CopyDown();

      var ans = new SignatureHelp();

      ans.setSignatures(new ArrayList<>());
      final Language language = ReadAction.compute(() -> PsiUtilCore.getLanguageAtOffset(psiFile, offset));
      final ParameterInfoHandler<PsiElement, Object>[] handlers =
          ShowParameterInfoHandler.getHandlers(project, language, psiFile.getViewProvider().getBaseLanguage());
      var disposable = Disposer.newDisposable();

      var editor = WriteAction.computeAndWait(() -> EditorUtil.createEditor(disposable, psiFile, position));

      final ShowParameterInfoContext context = new ShowParameterInfoContext(
          editor,
          project,
          psiFile,
          offset,
          -1,
          false,
          false
      );

      WriteAction.runAndWait(() -> {
        DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
          for (ParameterInfoHandler<PsiElement, Object> handler : handlers) {
            PsiElement element = handler.findElementForParameterInfo(context);
            if (element != null) {
              return (Runnable)() -> DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
                if (element.isValid()) {
                  handler.showParameterInfo(element, context);
                  ParameterInfoControllerBase controller = ParameterInfoControllerBase.createParameterInfoController(
                      project, editor, offset, context.getItemsToShow(), false, element, handler, true, false);;
                  var o = controller.getObjects()[0];
                  var modelContext = new MyParameterContext(false, element);
                  DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> handler.updateUI(o, modelContext));
                  LOG.warn(modelContext.model.signatures.toString());
                }
              });
            }
          }
          return (Runnable) () -> {};
        }).run();
      });
      return ans;
    } finally {
      cancelChecker.checkCanceled();
    }
  }

  private class MyParameterContext implements ParameterInfoUIContextEx {
    private final boolean mySingleParameterInfo;
    private int i;
    private final ParameterInfoControllerBase.Model model = new ParameterInfoControllerBase.Model();
    private PsiElement parameterOwner;

    private MyParameterContext(boolean singleParameterInfo, PsiElement parameterOwner) {
      mySingleParameterInfo = singleParameterInfo;
      this.parameterOwner = parameterOwner;
    }

    @Override
    public String setupUIComponentPresentation(@NlsContexts.Label String text,
                                               int highlightStartOffset,
                                               int highlightEndOffset,
                                               boolean isDisabled,
                                               boolean strikeout,
                                               boolean isDisabledBeforeHighlight,
                                               java.awt.Color background) {
      List<String> split = StringUtil.split(text, ",", false);
      StringBuilder plainLine = new StringBuilder();
      final List<Integer> startOffsets = new ArrayList<>();
      final List<Integer> endOffsets = new ArrayList<>();

      TextRange highlightRange = highlightStartOffset >=0 && highlightEndOffset >= highlightStartOffset ?
          new TextRange(highlightStartOffset, highlightEndOffset) :
          null;
      for (int j = 0; j < split.size(); j++) {
        String line = split.get(j);
        int startOffset = plainLine.length();
        startOffsets.add(startOffset);
        plainLine.append(line);
        int endOffset = plainLine.length();
        endOffsets.add(endOffset);
        if (highlightRange != null && highlightRange.intersects(new TextRange(startOffset, endOffset))) {
          model.current = j;
        }
      }
      ParameterInfoControllerBase.SignatureItem item = new ParameterInfoControllerBase.SignatureItem(plainLine.toString(), strikeout, isDisabled,
          startOffsets, endOffsets);
      model.signatures.add(item);

      return text;
    }

    @Override
    public void setupRawUIComponentPresentation(@NlsContexts.Label String htmlText) {
      ParameterInfoControllerBase.RawSignatureItem item = new ParameterInfoControllerBase.RawSignatureItem(htmlText);

      model.current = getCurrentParameterIndex();
      model.signatures.add(item);
    }

    @Override
    public String setupUIComponentPresentation(final String[] texts, final EnumSet<Flag>[] flags, final java.awt.Color background) {
      return String.join(", ", texts);
    }

    @Override
    public void setEscapeFunction(@Nullable Function<? super String, String> escapeFunction) {
    }

    @Override
    public boolean isUIComponentEnabled() {
      return false;
    }

    @Override
    public void setUIComponentEnabled(boolean enabled) {
    }

    public boolean isLastParameterOwner() {
      return false;
    }

    @Override
    public int getCurrentParameterIndex() {
      return i;
    }

    @Override
    public PsiElement getParameterOwner() {
      return parameterOwner;
    }

    @Override
    public boolean isSingleOverload() {
      return false;
    }

    @Override
    public boolean isSingleParameterInfo() {
      return mySingleParameterInfo;
    }

    private boolean isHighlighted() {
      return false;
    }

    @Override
    public Color getDefaultParameterColor() {
      return JBColor.MAGENTA;
    }
  }
}
