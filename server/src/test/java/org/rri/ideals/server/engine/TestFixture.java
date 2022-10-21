package org.rri.ideals.server.engine;

import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.LspPath;

import java.io.IOException;
import java.nio.file.Path;

public abstract class TestFixture {
  @NotNull
  private final Path testDataPath;

  protected TestFixture(@NotNull Path testDataPath) {
    this.testDataPath = testDataPath;
  }

  abstract void copyDirectoryToProject(@NotNull Path relativeDirectoryPath) throws IOException;

  abstract void copyFileToProject(@NotNull Path relativeFilePath) throws IOException;

  @NotNull
  abstract LspPath writeFileToProject(@NotNull String fileRelativePath, @NotNull String data) throws IOException;

  @NotNull
  public Path getTestDataPath() {
    return testDataPath;
  }
}
