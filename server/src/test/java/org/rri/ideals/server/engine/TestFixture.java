package org.rri.ideals.server.engine;

import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.LspPath;

import java.io.IOException;
import java.nio.file.Path;

public interface TestFixture {
  void copyDirectoryToProject(@NotNull Path sourceDirectory) throws IOException;

  void copyFileToProject(@NotNull Path filePath) throws IOException;

  @NotNull LspPath writeFileToProject(@NotNull String filePath, @NotNull String data) throws IOException;
}
