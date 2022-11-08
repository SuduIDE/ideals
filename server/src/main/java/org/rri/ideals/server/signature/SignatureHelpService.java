package org.rri.ideals.server.signature;

import com.intellij.codeInsight.hint.ParameterInfoControllerBase;
import com.intellij.codeInsight.hint.ParameterInfoListener;
import com.intellij.codeInsight.hint.ShowParameterInfoContext;
import com.intellij.codeInsight.hint.ShowParameterInfoHandler;
import com.intellij.lang.Language;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureInformation;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Tuple;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.util.EditorUtil;
import org.rri.ideals.server.util.LspProgressIndicator;
import org.rri.ideals.server.util.MiscUtil;

import java.util.ArrayList;

@Service(Service.Level.PROJECT)
final public class SignatureHelpService implements Disposable {
  private static final Logger LOG = Logger.getInstance(SignatureHelpService.class);
  @NotNull
  private final Project project;

  @Nullable
  private Runnable flushRunnable = null;

  public SignatureHelpService(@NotNull Project project) {
    this.project = project;
  }

  @Override
  public void dispose() {
  }

  @Nullable
  public SignatureHelp computeSignatureHelp(@NotNull LspPath path,
                                            @NotNull Position position,
                                            CancelChecker cancelChecker) {
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

      final Language language = ReadAction.compute(() -> PsiUtilCore.getLanguageAtOffset(psiFile, offset));
      // This assignment came from ShowParameterInfoHandler, IDEA 203.5981.155
      @SuppressWarnings("unchecked") final ParameterInfoHandler<PsiElement, Object>[] handlers =
          ShowParameterInfoHandler.getHandlers(project, language, psiFile.getViewProvider().getBaseLanguage());

      var editor = WriteAction.computeAndWait(() -> EditorUtil.createEditor(disposable, psiFile, position));

      final ShowParameterInfoContext context = new ShowParameterInfoContext(
          editor, project, psiFile, offset, -1, false, false);

      boolean isHandled = findAndUseValidHandler(handlers, context);
      if (!isHandled) {
        return MiscUtil.with(new SignatureHelp(),
            signatureHelp -> signatureHelp.setSignatures(new ArrayList<>()));
      }
      WriteAction.runAndWait(() -> PsiDocumentManager.getInstance(project).commitAllDocuments());
      if (ApplicationManager.getApplication().isUnitTestMode() && flushRunnable != null) {
        flushRunnable.run();
      }
      return ProgressManager.getInstance().runProcess(
          SignatureHelpService::createSignatureHelpFromListener, new LspProgressIndicator(cancelChecker));
    } finally {
      WriteAction.runAndWait(() -> Disposer.dispose(disposable));
      cancelChecker.checkCanceled();
    }
  }

  private static Boolean findAndUseValidHandler(ParameterInfoHandler<PsiElement, Object>[] handlers, ShowParameterInfoContext context) {
    return ReadAction.compute(() -> {
      for (ParameterInfoHandler<PsiElement, Object> handler : handlers) {
        PsiElement element = handler.findElementForParameterInfo(context);
        if (element != null && element.isValid()) {
          handler.showParameterInfo(element, context);
          return true;
        }
      }
      return false;
    });
  }

  private static SignatureHelp createSignatureHelpFromListener() {
    SignatureHelp ans = new SignatureHelp();
    for (var listener : ParameterInfoListener.EP_NAME.getExtensionList()) {
      if (listener instanceof MyParameterInfoListener myListener) {
        ParameterInfoControllerBase.Model model;
        try {
          model = myListener.queue.take();
        } catch (InterruptedException e) {
          throw MiscUtil.wrap(e);
        }
        ans.setSignatures(model.signatures.stream().map(signatureIdeaItemModel -> {
              var signatureItem = (ParameterInfoControllerBase.SignatureItem)signatureIdeaItemModel;
              var signatureInformation = new SignatureInformation();
              var parametersInformation = new ArrayList<ParameterInformation>();
              for (int i = 0; i < signatureItem.startOffsets.size(); i++) {
                int startOffset = signatureItem.startOffsets.get(i);
                int endOffset = signatureItem.endOffsets.get(i);
                parametersInformation.add(
                    MiscUtil.with(new ParameterInformation(),
                        parameterInformation ->
                            parameterInformation.setLabel(Tuple.two(startOffset, endOffset))
                    ));
              }
          signatureInformation.setParameters(parametersInformation);
          signatureInformation.setActiveParameter(model.current == -1 ? null : model.current);
          signatureInformation.setLabel(signatureItem.text);
          return signatureInformation;
        }).toList());
        ans.setActiveSignature(model.highlightedSignature == -1 ? null : model.highlightedSignature);
        break;
      }
    }
    return ans;
  }

  @TestOnly
  public void setEdtFlushRunnable(Runnable runnable) {
    this.flushRunnable = runnable;
  }
}
