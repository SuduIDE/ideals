package org.rri.ideals.server.bootstrap;

import com.intellij.openapi.application.ApplicationStarter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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

//  public @NotNull CompletableFuture<CliResult> processCommand(@NotNull List<String> args, @Nullable String currentDirectory) {
//    return buildRunner(args)
//            .launch()
//            .thenApply(unused -> new CliResult(0, "LSP Server done"));
//  }

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

  @Override
  public int getRequiredModality() {
    return ApplicationStarter.NOT_IN_EDT;
  }

  @Override
  public void main(@NotNull List<String> args) {
    ;
  }

  @Override
  public void premain(@NotNull List<String> args) {
    if (!checkArguments(args)) {
      System.err.println(getUsageMessage());
      System.exit(1);
    }
  }
}