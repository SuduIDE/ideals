//package org.rri.ideals.server.engine;
//
//import org.apache.commons.io.FileUtils;
//import org.jetbrains.annotations.NotNull;
//import org.rri.ideals.server.LspPath;
//import org.rri.ideals.server.util.MiscUtil;
//
//import java.io.FileWriter;
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//
//public class DefaultTestFixture implements TestFixture {
//
//  @NotNull
//  private final Path sandboxPath;
//  @NotNull
//  private final Path testDataPath;
//
//  public DefaultTestFixture(@NotNull Path sandboxPath, @NotNull Path testDataPath) {
//    try {
//      this.testDataPath = testDataPath;
//      if (!Files.exists(sandboxPath)) {
//        Files.createDirectories(sandboxPath);
//      }
//      if (!Files.isDirectory(sandboxPath)) {
//        throw new RuntimeException("Path is not a directory. Path: " + sandboxPath);
//      }
//      this.sandboxPath = sandboxPath;
//      try (final var files = Files.newDirectoryStream(sandboxPath)) {
//        files.forEach(MiscUtil.toConsumer(path -> {
//          if (Files.isDirectory(path)) {
//            FileUtils.deleteDirectory(path.toFile());
//          } else {
//            Files.delete(path);
//          }
//        }));
//      }
//    } catch (IOException e) {
//      throw MiscUtil.wrap(e);
//    }
//  }
//
//  @Override
//  public void copyDirectoryToProject(@NotNull Path sourceDirectory) throws IOException {
//    try (final var files =  Files.walk(sourceDirectory)) {
//      files.forEach(MiscUtil.toConsumer(source -> {
//        if (!Files.isDirectory(source)) {
//          Path target = Paths.get(sandboxPath.toString(), testDataPath.relativize(source).toString());
//
//          if (!Files.exists(target.getParent())) {
//            Files.createDirectories(target.getParent());
//          }
//          Files.copy(source, target);
//        }
//      }));
//    }
//  }
//
//  @Override
//  public void copyFileToProject(@NotNull Path filePath) throws IOException {
//    final var name = filePath.toFile().getName();
//    Files.copy(filePath, Paths.get(sandboxPath.toString(), name));
//  }
//
//  @Override
//  public @NotNull LspPath writeFileToProject(@NotNull String filePath, @NotNull String data) throws IOException {
//    final var realPath = Paths.get(sandboxPath.toString(), filePath);
//    Files.createDirectories(realPath.getParent());
//    try (final FileWriter writer = new FileWriter(realPath.toString())) {
//      writer.write(data);
//      return LspPath.fromLocalPath(realPath);
//    }
//  }
//}
