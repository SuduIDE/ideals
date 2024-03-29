package org.rri.ideals.server.bootstrap;

import com.intellij.ide.CliResult;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationStarter;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.Future;


public class LspServerStarter implements ApplicationStarter {

  public LspServerStarter() {
  }

  @NotNull
  // this method is deprecated since 223.*
  // Before 223.* idea was determining what starter need to be started by this method, but since
  // 223.* we can set <id> key in plugin.xml instead
  public String getCommandName() {
    return "lsp-server";
  }

  public boolean isHeadless() {
    return true;
  }

  @NotNull
  public String getUsageMessage() {
    return "Run \"idea lsp-server\"";
  }

  public boolean canProcessExternalCommandLine() {
    return false;
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

  @SuppressWarnings("UnstableApiUsage")
  @Override
  public void main(@NotNull List<String> args) {
    try {
      int exitCode;
      try {
        Future<CliResult> commandFuture = buildRunner(args)
            .launch()
            .thenApply(unused -> new CliResult(0, "LSP Server done"));
        CliResult result = commandFuture.get();
        if (result.message() != null) {
          System.out.println(result.message());
        }
        exitCode = result.exitCode();
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
    // FileDocumentManager.getInstance().saveAllDocuments();  // LSP server must not save documents
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