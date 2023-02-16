package org.rri.ideals.server.diagnostics;

import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.rri.ideals.server.LspContext;
import org.rri.ideals.server.LspLightBasePlatformTestCase;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.mocks.MockLanguageClient;

public abstract class DiagnosticsTestBase extends LspLightBasePlatformTestCase {
  @Before
  public void setupContext() {
    LspContext.createContext(getProject(),
        new MockLanguageClient(),
        new ClientCapabilities()
    );
  }

  @NotNull
  protected MockLanguageClient getClient() {
    return (MockLanguageClient) LspContext.getContext(getProject()).getClient();
  }

  @NotNull
  protected PublishDiagnosticsParams runAndGetDiagnostics(@NotNull PsiFile file) {
    getClient().resetDiagnosticsResult();
    getProject().getService(DiagnosticsService.class).launchDiagnostics(LspPath.fromVirtualFile(file.getVirtualFile()));
    return getClient().waitAndGetDiagnosticsPublished();
  }
}
