package org.rri.ideals.server.lsp;

import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.engine.DefaultTestFixture;
import org.rri.ideals.server.engine.TestEngine;

public abstract class LspServerTestWithEngineBase extends LspServerTestBase {
  private final TestEngine engine =
      new TestEngine(new DefaultTestFixture(getProjectPath(), getTestDataRoot().resolve(getMarkedTestProjectRelativePath())));

  public TestEngine getEngine() {
    return engine;
  }

  protected abstract @NotNull String getMarkedTestProjectRelativePath();

  @Override
  public void setupServer() {
    engine.initSandbox("");
    super.setupServer();
  }
}
