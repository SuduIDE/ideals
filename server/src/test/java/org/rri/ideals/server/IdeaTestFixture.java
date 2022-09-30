package org.rri.ideals.server;

import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class IdeaTestFixture implements TestFixture {
  @NotNull
  private final CodeInsightTestFixture fixture;
  @NotNull
  private final Path baseDirPath;

  public IdeaTestFixture(@NotNull CodeInsightTestFixture fixture) {
    this.fixture = fixture;
    baseDirPath = Paths.get(fixture.getTestDataPath());
  }


  @Override
  public void copyDirectoryToProject(@NotNull Path sourceDirectory) {
    fixture.copyDirectoryToProject(TestUtil.getPathTail(baseDirPath, sourceDirectory), "");
  }

  @Override
  public void copyFileToProject(@NotNull Path filePath) {
    fixture.copyFileToProject(TestUtil.getPathTail(baseDirPath, filePath));
  }

  @Override
  public @NotNull LspPath writeFileToProject(@NotNull String filePath, @NotNull String data) {
    final var file = Optional.ofNullable(fixture.addFileToProject(filePath, data))
        .orElseThrow(() -> new RuntimeException("Fixture can't create file"));
    return LspPath.fromVirtualFile(file.getVirtualFile());
  }
}
