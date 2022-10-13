package org.rri.ideals.server.engine;

import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.LspPath;

import java.nio.file.Path;

public interface TestFixture {
  void copyDirectoryToProject(@NotNull Path sourceDirectory);

  void copyFileToProject(@NotNull Path filePath);

  @NotNull LspPath writeFileToProject(@NotNull String filePath, @NotNull String data);
}
