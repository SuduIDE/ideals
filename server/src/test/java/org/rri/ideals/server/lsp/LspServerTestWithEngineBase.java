//package org.rri.ideals.server.lsp;
//
//import org.jetbrains.annotations.NotNull;
////import org.rri.ideals.server.engine.DefaultTestFixture;
//import org.rri.ideals.server.engine.TestEngine;
//
//import java.nio.file.Path;
//
//public abstract class LspServerTestWithEngineBase extends LspServerTestBase {
//  private final TestEngine engine = new TestEngine(getTargetProjectPath(), fixture1);
//
//  public TestEngine getEngine() {
//    return engine;
//  }
//
//  protected abstract @NotNull Path getTargetProjectPath();
//
//  @Override
//  public void setupServer() {
//    engine.initSandbox(new DefaultTestFixture(getProjectPath(), getTargetProjectPath()));
//    super.setupServer();
//  }
//}
