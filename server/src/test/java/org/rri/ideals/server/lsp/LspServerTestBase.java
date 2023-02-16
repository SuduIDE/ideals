package org.rri.ideals.server.lsp;

import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.testFramework.HeavyPlatformTestCase;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.ideals.server.LspServer;
import org.rri.ideals.server.TestUtil;
import org.rri.ideals.server.mocks.MockLanguageClient;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@RunWith(JUnit4.class)
public abstract class LspServerTestBase extends HeavyPlatformTestCase {

  private LspServer server;

  private MockLanguageClient client;

  @Override
  protected void setUp() throws Exception {
    System.setProperty("idea.log.debug.categories", "#org.rri");
    super.setUp();
  }

  @NotNull
  protected Path getTestDataRoot() {
    return Paths.get("test-data").toAbsolutePath();
  }

  @NotNull
  final protected Path getProjectPath() {
    return getTestDataRoot().resolve(getProjectRelativePath());
  }

  protected abstract String getProjectRelativePath();


  @NotNull
  protected final LspServer server() {
    return server;
  }

  @NotNull
  protected final MockLanguageClient client() {
    return client;
  }

  @Override
  protected void setUpProject() {
    // no IDEA project is created by default
  }

  protected void setupInitializeParams(@NotNull InitializeParams params) {
    Path projectPath = getProjectPath();

    params.setWorkspaceFolders(List.of(new WorkspaceFolder(projectPath.toUri().toString())));

    final var clientCapabilities = new ClientCapabilities();
    setupClientCapabilities(clientCapabilities);
    params.setCapabilities(clientCapabilities);
  }

  @SuppressWarnings("unused")
  protected void setupClientCapabilities(@NotNull ClientCapabilities clientCapabilities) {
    // do nothing by default
  }

  protected void initializeServer() {
    final var initializeParams = new InitializeParams();
    setupInitializeParams(initializeParams);
    TestUtil.getNonBlockingEdt(server.initialize(initializeParams), 30000);
  }

  @Before
  public void setupServer() {
    server = new LspServer();
    client = new MockLanguageClient();
    server.connect(client);
    initializeServer();
  }

  @After
  public void stopServer() {
    server.stop();
    ProjectManagerEx.getInstanceEx().closeAndDisposeAllProjects(false);
  }

  @Override
  protected boolean isIconRequired() {
    return true;
  }
}
