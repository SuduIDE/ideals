package org.rri.server.diagnostics;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.rri.server.LspContext;
import org.rri.server.LspPath;
import org.rri.server.mocks.MockLanguageClient;

public abstract class DiagnosticsTestBase extends BasePlatformTestCase {
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
