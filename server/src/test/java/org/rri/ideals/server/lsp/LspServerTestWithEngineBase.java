package org.rri.ideals.server.lsp;

import com.intellij.openapi.vfs.LocalFileSystem;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.engine.DefaultTestFixture;
import org.rri.ideals.server.engine.TestEngine;

public abstract class LspServerTestWithEngineBase extends LspServerTestBase {
  private final TestEngine engine =
      new TestEngine(new DefaultTestFixture(getProjectPath(), getTestDataRoot().resolve(getTestDataRelativePath())));

  public TestEngine getEngine() {
    return engine;
  }

  protected abstract @NotNull String getTestDataRelativePath();

  @Override
  protected void setUp() throws Exception {
    engine.initSandbox("");
    super.setUp();
    LocalFileSystem.getInstance().refresh(false);
  }

  @Override
  public void setupServer() {
    super.setupServer();
  }
}
