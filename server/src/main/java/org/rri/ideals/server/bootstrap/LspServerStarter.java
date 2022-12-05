package org.rri.ideals.server.bootstrap;

import com.intellij.ide.CliResult;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;


public class LspServerStarter implements ApplicationStarter {

  public LspServerStarter() {
  }

  @NotNull
  public String getCommandName() {
    return "lsp-server";
  }

  public boolean isHeadless() {
    return true;
  }

  @NotNull
  public String getUsageMessage() {
    return "Run \"idea " + getCommandName() + "\"";
  }

  public boolean canProcessExternalCommandLine() {
    return false;
  }

  private @NotNull CompletableFuture<CliResult> processCommand(@NotNull List<String> args) {
    return buildRunner(args)
            .launch()
            .thenApply(unused -> new CliResult(0, "LSP Server done"));
  }

  @NotNull
  private static LspServerRunnerBase buildRunner(@NotNull List<String> args) {
    assert args.size() >= 1 : "insufficient arguments";

    if (args.size() > 1) {
      var transportType = args.get(1);

      if (transportType.equals("tcp")) {
        var runner = new TcpLspServerRunner();

        if (args.size() > 2) {
          runner.setPort(Integer.parseInt(args.get(2)));
        }

        return runner;
      }
    }

    return new StdioLspServerRunner();
  }

  @Override
  public int getRequiredModality() {
    return ApplicationStarter.NOT_IN_EDT;
  }

  @Override
  public void main(@NotNull List<String> args) {
    try {
      int exitCode;
      try {
        Future<CliResult> commandFuture = processCommand(args);
        CliResult result = commandFuture.get();
        if (result.getMessage() != null) {
          System.out.println(result.getMessage());
        }
        exitCode = result.getExitCode();
      } finally {
        ApplicationManager.getApplication().invokeAndWait(this::saveAll);
      }
      System.exit(exitCode);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    } catch (Throwable t) {
      t.printStackTrace();
      System.exit(2);
    }
  }

  private void saveAll() {
    FileDocumentManager.getInstance().saveAllDocuments();
    ApplicationManager.getApplication().saveSettings();
  }

  @Override
  public void premain(@NotNull List<String> args) {
    if (args.size() < 1) {
      System.err.println(getUsageMessage());
      System.exit(1);
    }
  }
}