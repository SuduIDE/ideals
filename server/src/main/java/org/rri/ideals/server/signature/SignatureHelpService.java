package org.rri.ideals.server.signature;

import com.intellij.codeInsight.signatureHelp.LanguageSignatureHelp;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import io.github.furstenheim.CopyDown;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.util.MiscUtil;

import java.util.ArrayList;

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
      final var doc = MiscUtil.getDocument(psiFile);
      assert doc != null;
      final var offset = MiscUtil.positionToOffset(doc, position);
      var ideaSignatureHelpResult = LanguageSignatureHelp.INSTANCE.forLanguage(psiFile.getLanguage()).getSignatureHelp(psiFile, offset);

      var ans = new SignatureHelp();

      var htmlToMarkdownConverter = new CopyDown();

      ans.setSignatures(new ArrayList<>());
      ideaSignatureHelpResult.getSignatures().stream().peek(ideaSignatureInfo -> {
        ans.getSignatures().add(MiscUtil.with(new SignatureInformation(),
            signatureInfo -> {
              signatureInfo.setLabel(ideaSignatureInfo.getLabel());
              signatureInfo.setDocumentation(
                  new MarkupContent(
                      MarkupKind.MARKDOWN,
                      htmlToMarkdownConverter.convert(ideaSignatureInfo.getDocumentation())));
              signatureInfo.setParameters(
                  ideaSignatureInfo.getParameterInformation().stream().map(
                      ideaParameterInfo ->
                          MiscUtil.with(new ParameterInformation(),
                              parameterInformation -> {
                                parameterInformation.setLabel(ideaSignatureInfo.getLabel());
                                parameterInformation.setDocumentation(
                                    new MarkupContent(MarkupKind.MARKDOWN, htmlToMarkdownConverter.convert(ideaParameterInfo.getDocumentation())));
                              })).toList()
              );
            }));
      });

      assert ideaSignatureHelpResult != null;
      LOG.warn(ans.toString());
      return ans;
    } finally {
      cancelChecker.checkCanceled();
    }
  }
}
