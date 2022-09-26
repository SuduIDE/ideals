package org.rri.ideals.server;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;

import java.nio.file.Path;
import java.util.List;
import java.util.Stack;

abstract public class TestEngine {
  public interface LspTest {
    Object getParams();
    Object getAnswer();
  }

  protected interface Token {
  }

  private final Path rootDirectory;
  private final List<Path> pathsToFiles;
  private List<StringBuilder> processedTexts;
  private List<? extends LspTest> tests;

  protected TestEngine(Path rootDirectory, List<Path> pathsToFiles) {
    this.rootDirectory = rootDirectory;
    this.pathsToFiles = pathsToFiles;
  }

  public void processLspTests() {
    // Pass the text and find tokens
  }

  public void writeFilesToSandbox(Path sandboxDirectory) {
    // Write files in directory
  }

  // RETURN virtualFile to fixture directory
  public VirtualFile copyFilesToFixture(CodeInsightTestFixture fixture) {
    // Write files in fixture
    return null;
  }

  public List<? extends LspTest> getTests() {
    return tests;
  }

  abstract protected List<? extends LspTest> processTokens(Stack<? extends Token> tokens);

  abstract protected Token parseSingeToken(int offset, StringBuilder text);
}
