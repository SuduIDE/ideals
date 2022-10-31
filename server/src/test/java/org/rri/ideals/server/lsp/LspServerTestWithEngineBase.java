package org.rri.ideals.server.lsp;

import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.engine.DefaultTestFixture;
import org.rri.ideals.server.engine.TestEngine;
import org.rri.ideals.server.util.MiscUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Random;

public abstract class LspServerTestWithEngineBase extends LspServerTestBase {
  private TestEngine engine;
  private static final Random random = new Random();
  private String sandboxRelativePath;

  public TestEngine getEngine() {
    return engine;
  }

  protected abstract @NotNull String getTestDataRelativePath();

  @Override
  final protected String getProjectRelativePath() {
    return this.sandboxRelativePath;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  @Override
  public void setupServer() {
    try {
      var rootOfSandboxesName = "sandbox";
      var rootOfSandboxesPath = getTestDataRoot().resolve(rootOfSandboxesName);
      if (!Files.exists(rootOfSandboxesPath)) {
        Files.createDirectories(rootOfSandboxesPath);
      }
      if (!Files.isDirectory(rootOfSandboxesPath)) {
        throw new RuntimeException("Path is not a directory. Path: " + rootOfSandboxesPath);
      }
      try (final var files = Files.newDirectoryStream(rootOfSandboxesPath)) {
        boolean isUnique = false;
        int candidate = random.nextInt();
        while(!isUnique) {
          String candidateName = candidate + "-" + this.getName();
          for (var file : files) {
            if (file.getFileName().toString().equals(candidateName)) {
              candidate = random.nextInt();
              break;
            }
          }
          this.sandboxRelativePath = rootOfSandboxesName + "/" + candidateName;
          isUnique = true;
        }

        var sandboxPath = this.getTestDataRoot().resolve(this.sandboxRelativePath);

        Files.createDirectories(sandboxPath);
        this.engine = new TestEngine(new DefaultTestFixture(sandboxPath, getTestDataRoot().resolve(getTestDataRelativePath())));
      }
    } catch (IOException e) {
      throw MiscUtil.wrap(e);
    }
    engine.initSandbox("");

    super.setupServer();
  }
}
