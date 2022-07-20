package org.rri.server.lsp;

import org.eclipse.lsp4j.InitializeParams;
import org.junit.Assert;
import org.junit.Test;
import org.rri.server.TestUtil;

import java.nio.file.Paths;
import java.util.Objects;

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

    TestUtil.getNonBlockingEdt(initializeResult, 30000);

    Assert.assertEquals("project has unexpected location",
            getProjectPath(),
            Paths.get(Objects.requireNonNull(server().getProject().getBasePath())));
  }
}
