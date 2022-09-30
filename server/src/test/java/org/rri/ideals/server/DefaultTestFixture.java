package org.rri.ideals.server;

import org.jetbrains.annotations.NotNull;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DefaultTestFixture implements TestFixture {

  @NotNull
  private final Path sandboxPath;

  public DefaultTestFixture(@NotNull Path sandboxPath) {
    this.sandboxPath = sandboxPath;
  }

  @Override
  public void copyDirectoryToProject(@NotNull Path sourceDirectory) throws RuntimeException {
    try (final var files =  Files.walk(sourceDirectory)) {
      files.forEach(source -> {
        if (!Files.isDirectory(source)) {
          Path target = Paths.get(sandboxPath.toString(), TestUtil.getPathTail(sourceDirectory, source));
          try {
            Files.copy(source, target);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      });
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void copyFileToProject(@NotNull Path filePath) {
    try {
      final var name = filePath.toFile().getName();
      Files.copy(filePath, Paths.get(sandboxPath.toString(), name));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public @NotNull LspPath writeFileToProject(@NotNull String filePath, @NotNull String data) {
    final var realPath = Paths.get(sandboxPath.toString(), filePath);
    try {
      Files.createDirectories(realPath.getParent());
      try (final FileWriter writer = new FileWriter(realPath.toString())) {
        writer.write(data);
        return LspPath.fromLocalPath(realPath);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
