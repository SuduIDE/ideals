package org.rri.ideals.server.engine;

import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.TestUtil;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DefaultTestFixture implements TestFixture {

  @NotNull
  private final Path sandboxPath;
  @NotNull
  private final Path targetDirPath;

  public DefaultTestFixture(@NotNull Path sandboxPath, @NotNull Path targetDirPath) throws IOException {
    this.targetDirPath = targetDirPath;
    if (!Files.exists(sandboxPath)) {
      Files.createDirectories(sandboxPath);
    }
    if (!Files.isDirectory(sandboxPath)) {
      throw new RuntimeException("Path is not a directory. Path: " + sandboxPath);
    }
    this.sandboxPath = sandboxPath;
    try (final var files = Files.newDirectoryStream(sandboxPath)) {
        files.forEach(path -> {
          try {
            if (Files.isDirectory(path)) {
              FileUtils.deleteDirectory(path.toFile());
            } else {
              Files.delete(path);
            }
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
    }
  }

  @Override
  public void copyDirectoryToProject(@NotNull Path sourceDirectory) throws RuntimeException {
    try (final var files =  Files.walk(sourceDirectory)) {
      files.forEach(source -> {
        if (!Files.isDirectory(source)) {
          Path target = Paths.get(sandboxPath.toString(), TestUtil.getPathTail(targetDirPath, source));
          try {
            if (!Files.exists(target.getParent())) {
              Files.createDirectories(target.getParent());
            }
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
