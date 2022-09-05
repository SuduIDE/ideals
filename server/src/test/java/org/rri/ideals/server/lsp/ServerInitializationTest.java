package org.rri.ideals.server.lsp;

import com.intellij.openapi.project.Project;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.Assert;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.rri.ideals.server.TestUtil;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

public class ServerInitializationTest extends LspServerTestBase {

  @Override
  protected String getProjectRelativePath() {
    return "lsp/project1";
  }

  @Override
  protected void initializeServer() {
    // do nothing as we test the initialization process itself here
  }

  @Test
  public void testInitializeAndSwitchToAnotherWorkspaceThenSwitchToEmptyWorkspace() {
    final var initializeParams = new InitializeParams();
    setupInitializeParams(initializeParams);

    InitializeResult initializeResult;

    initializeResult = TestUtil.getNonBlockingEdt(server().initialize(initializeParams), 30000);
    Assert.assertNotNull(initializeResult.getCapabilities().getTextDocumentSync());

    Project project1 = server().getProject();

    Assert.assertEquals("project has unexpected location",
        getProjectPath(),
        Paths.get(Objects.requireNonNull(project1.getBasePath())));

    Path project2Root = getTestDataRoot().resolve("lsp/project2");
    initializeParams.setWorkspaceFolders(List.of(new WorkspaceFolder(project2Root.toUri().toString())));

    initializeResult = TestUtil.getNonBlockingEdt(server().initialize(initializeParams), 30000);

    Assert.assertTrue("project should have been closed and disposed: " + project1, project1.isDisposed());

    Assert.assertNotNull(initializeResult.getCapabilities().getTextDocumentSync());

    Project project2 = server().getProject();

    Assert.assertEquals("project has unexpected location",
        project2Root,
        Paths.get(Objects.requireNonNull(project2.getBasePath())));

    initializeParams.setWorkspaceFolders(null);

    initializeResult = TestUtil.getNonBlockingEdt(server().initialize(initializeParams), 30000);

    Assert.assertNull(initializeResult.getCapabilities().getTextDocumentSync());

    Assertions.assertThrows(IllegalStateException.class, () -> server().getProject());
    Assert.assertTrue("project should have been closed and disposed: " + project2, project2.isDisposed());
  }
}
