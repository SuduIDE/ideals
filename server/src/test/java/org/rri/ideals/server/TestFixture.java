package org.rri.ideals.server;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public interface TestFixture {
  void copyDirectoryToProject(@NotNull Path sourceDirectory);

  void copyFileToProject(@NotNull Path filePath);

  @NotNull LspPath writeFileToProject(@NotNull String filePath, @NotNull String data);
}
