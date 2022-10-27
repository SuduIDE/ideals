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
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.utils.parameterInfo.MockUpdateParameterInfoContext;
import com.intellij.ui.JBColor;
import com.intellij.util.Function;
import com.intellij.util.indexing.DumbModeAccessType;
import io.github.furstenheim.CopyDown;
import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureInformation;
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
    var disposable = Disposer.newDisposable();
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
      // todo in model we have info about current (aka current parameter index) and highlighted signature (current sig item)
      WriteAction.runAndWait(() -> {
        DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
          for (ParameterInfoHandler<PsiElement, Object> handler : handlers) {
            PsiElement element = handler.findElementForParameterInfo(context);
            if (element != null) {
              return (Runnable)() -> DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
                if (element.isValid()) {
                  handler.showParameterInfo(element, context);
                  MockUpdateParameterInfoContext updateContext = new MockUpdateParameterInfoContext(
                      editor, psiFile
                  );
                  ParameterInfoControllerBase controller = ParameterInfoControllerBase.createParameterInfoController(
                      project, editor, offset, context.getItemsToShow(), context.getHighlightedElement(), element, handler, true, false);
                  var o = handler.findElementForUpdatingParameterInfo(updateContext);
                  assert o != null;
                  DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> handler.updateParameterInfo(o, updateContext));

                  controller.showHint(false, false); // todo
                  controller.updateComponent();
                  var modelContext = new MyParameterContext(false, element);
                  for (int i = 0; i < controller.getObjects().length; i++) {
                    var descriptor = controller.getObjects()[i];
                    modelContext.i = i;
                    if (descriptor.equals(controller.getHighlighted())) {
                      modelContext.model.highlightedSignature = i;
                    }

                    DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> handler.updateUI(descriptor, modelContext));
                  }
                  ans.setSignatures(modelContext.signatureItems.stream().map(signatureIdeaItem -> {
                    var signatureInformation = new SignatureInformation();
                    var parametersInformation = new ArrayList<ParameterInformation>();
                    for (int i = 0; i < signatureIdeaItem.startOffsets.size(); i++) {
                      int startOffset = signatureIdeaItem.startOffsets.get(i);
                      int endOffset = signatureIdeaItem.endOffsets.get(i);
                      parametersInformation.add(
                          MiscUtil.with(new ParameterInformation(),
                              parameterInformation ->
                                  parameterInformation.setLabel(signatureIdeaItem.text.substring(startOffset, endOffset))
                          ));
                    }
                    int labelEndOffset =
                        signatureIdeaItem.startOffsets.isEmpty() ? signatureIdeaItem.text.length() : signatureIdeaItem.startOffsets.get(0);

                    signatureInformation.setParameters(parametersInformation);
                    signatureInformation.setActiveParameter(modelContext.model.current == -1 ? null : modelContext.model.current);
                    signatureInformation.setLabel(signatureIdeaItem.text);
                    return signatureInformation;
                  }).toList());
                  ans.setActiveSignature(modelContext.model.highlightedSignature == -1 ? null : modelContext.model.highlightedSignature);
                }
              });
            }
          }
          return (Runnable) () -> {};
        }).run();
      });
      return ans;
    } finally {
      WriteAction.runAndWait(() -> Disposer.dispose(disposable));
      cancelChecker.checkCanceled();
    }
  }

  private static class MyParameterContext implements ParameterInfoUIContextEx {
    private final boolean mySingleParameterInfo;
    private int i;
    private final ParameterInfoControllerBase.Model model = new ParameterInfoControllerBase.Model();

    private final ArrayList<ParameterInfoControllerBase.SignatureItem> signatureItems = new ArrayList<>();
    private final PsiElement parameterOwner;

    private MyParameterContext(boolean singleParameterInfo, PsiElement parameterOwner) {
      mySingleParameterInfo = singleParameterInfo;
      this.parameterOwner = parameterOwner;
    }

    @Override
    public String setupUIComponentPresentation(@NotNull String text,
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
      signatureItems.add(item);

      return text;
    }

    @Override
    public void setupRawUIComponentPresentation(String htmlText) {
      // todo search ways where it is called
      // I found only by ctrl+up/down with completion hint settings enabled by now
      ParameterInfoControllerBase.RawSignatureItem item = new ParameterInfoControllerBase.RawSignatureItem(htmlText);

      model.current = getCurrentParameterIndex();
      model.signatures.add(item);
      // I skip this element, because it is not covered by lsp as it is in IDEA
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
    @Override
    public Color getDefaultParameterColor() {
      return JBColor.MAGENTA;
    }
  }
}
