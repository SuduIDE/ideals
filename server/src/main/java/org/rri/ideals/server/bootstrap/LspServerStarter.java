package org.rri.ideals.server.bootstrap;

import com.intellij.ide.CliResult;
import com.intellij.openapi.application.ApplicationStarterBase;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@SuppressWarnings("ALL")
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

//  @Override
//  public int getRequiredModality() {
//    return NOT_IN_EDT;
//  }

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

  @SuppressWarnings("UnstableApiUsage")
  @Nullable
  @Override
  protected Object executeCommand(@NotNull List<String> list, @Nullable String currendDirectory,
                                  @NotNull Continuation<? super CliResult> continuation) {
    try {
//      return processCommand(list, currendDirectory).get();
      var r = new TcpLspServerRunner();
      r.setPort(8989);
      r.prepareForListening();
      while (true) {
        var serverFuture = r.connectServer(r.waitForConnection());
        serverFuture.join();
        break;
      }

      return new CliResult(0, "OK");
//    } catch (InterruptedException e) {
//      throw new RuntimeException(e);
//    } catch (ExecutionException e) {
//      throw new RuntimeException(e);
//    }
    } finally {

    }
  }


}