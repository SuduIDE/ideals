package org.rri.server;

import org.eclipse.lsp4j.InitializeParams;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.file.Paths;
import java.util.Objects;

@RunWith(JUnit4.class)
public class ServerInitializationTest extends LspServerTestBase {

  @Override
  protected String getProjectRelativePath() {
    return "project";
  }

  @Override
  protected void initializeServer() {
    // do nothing as we test the initialization process itself here
  }

  @Test
  public void testInitialize() {

    final var initializeParams = new InitializeParams();
    setupInitializeParams(initializeParams);

    final var initializeResult = server().initialize(initializeParams);

    TestUtil.edtSafeGet(initializeResult);

    var expectedLocation = LspPath.fromLspUri(initializeParams.getWorkspaceFolders().get(0).getUri());

    Assert.assertEquals("project has unexpected location",
            expectedLocation.toPath(),
            Paths.get(Objects.requireNonNull(server().getProject().getBasePath())));
  }
}
