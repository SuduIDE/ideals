package org.rri.server;

import org.eclipse.lsp4j.InitializeParams;
import org.junit.Assert;
import org.junit.Test;

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

    TestUtil.edtSafeGet(initializeResult);

    Assert.assertEquals("project has unexpected location",
            getProjectPath(),
            Paths.get(Objects.requireNonNull(server().getProject().getBasePath())));
  }
}
