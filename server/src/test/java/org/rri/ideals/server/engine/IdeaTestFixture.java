package org.rri.ideals.server.engine;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.LspPath;

import java.nio.file.Path;
import java.nio.file.Paths;

public class IdeaTestFixture extends TestFixture {
  @NotNull
  private final CodeInsightTestFixture fixture;

  public IdeaTestFixture(@NotNull CodeInsightTestFixture fixture) {
    super(Paths.get(fixture.getTestDataPath()));
    this.fixture = fixture;
  }


  @Override
  public void copyDirectoryToProject(@NotNull Path relativeDirectoryPath) {
    fixture.copyDirectoryToProject(relativeDirectoryPath.toString(), "");
  }

  @Override
  public void copyFileToProject(@NotNull Path relativeFilePath) {
    fixture.copyFileToProject(relativeFilePath.toString());
  }

  @Override
  public @NotNull LspPath writeFileToProject(@NotNull String fileRelativePath, @NotNull String data) {
    fileRelativePath = FileUtil.toSystemIndependentName(fileRelativePath);
    final var file = fixture.addFileToProject(fileRelativePath, data);
    if (file == null) {
      throw new RuntimeException("Fixture can't create file");
    }
    return LspPath.fromVirtualFile(file.getVirtualFile());
  }
}
