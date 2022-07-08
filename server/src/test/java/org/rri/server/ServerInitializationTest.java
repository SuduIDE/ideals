package org.rri.server;

import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.testFramework.HeavyPlatformTestCase;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rri.server.mocks.MockLanguageClient;
import org.rri.server.util.MiscUtil;

import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

@RunWith(JUnit4.class)
public class ServerInitializationTest extends HeavyPlatformTestCase {

  private LspServer server;

  @Override
  protected void setUpProject() { }

  @Before
  public void setupServer() {
    server = new LspServer();
    server.connect(new MockLanguageClient());
  }

  @Test
  public void testInitialize() {
    final var projectPath = Paths.get("test-data/project").toAbsolutePath();

    final var initializeResult = server.initialize(MiscUtil.with(new InitializeParams(), it -> {
      it.setWorkspaceFolders(List.of(new WorkspaceFolder(projectPath.toUri().toString())));
      it.setCapabilities(new ClientCapabilities());
    }));

    TestUtil.edtSafeGet(initializeResult);

    Assert.assertEquals("project has unexpected location", projectPath, Paths.get(Objects.requireNonNull(server.getProject().getBasePath())));
  }

  @After
  public void stopServer() {
    server.stop();
    ProjectManagerEx.getInstanceEx().closeAndDisposeAllProjects(false);
  }
}
