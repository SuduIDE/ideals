package org.rri.ideals.server.bootstrap;

import com.intellij.ide.CliResult;
import com.intellij.openapi.application.ApplicationStarterBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class LspServerStarter extends ApplicationStarterBase {

  public LspServerStarter() {
    super(0);
  }

  @NotNull
  public String getCommandName() {
    return "lsp-server";
  }

  public boolean isHeadless() {
    return true;
  }

  @Override
  public int getRequiredModality() {
    return NOT_IN_EDT;
  }

  @NotNull
  public String getUsageMessage() {
    return "Run \"idea " + getCommandName() + "\"";
  }

  public boolean canProcessExternalCommandLine() {
    return false;
  }

  public @NotNull CompletableFuture<CliResult> processCommand(@NotNull List<String> args, @Nullable String currentDirectory) {
    return buildRunner(args)
            .launch()
            .thenApply(unused -> new CliResult(0, "LSP Server done"));
  }

  @NotNull
  private static LspServerRunnerBase buildRunner(@NotNull List<String> args) {
    assert args.size() >= 1 : "insufficient arguments";

    if(args.size() > 1) {
      var transportType = args.get(1);

      if (transportType.equals("tcp")) {
        var runner = new TcpLspServerRunner();

        if(args.size() > 2) {
          runner.setPort(Integer.parseInt(args.get(2)));
        }

        return runner;
      }
    }

    return new StdioLspServerRunner();
  }
}