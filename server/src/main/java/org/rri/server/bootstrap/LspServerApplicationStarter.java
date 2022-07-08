package org.rri.server.bootstrap;

import com.intellij.ide.CliResult;
import com.intellij.openapi.application.ApplicationStarterBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class LspServerApplicationStarter extends ApplicationStarterBase {

  public LspServerApplicationStarter() {
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
    return new LspServerRunner()
            .launch(8989)
            .thenApply(unused -> new CliResult(0, "LSP Server done"));  // todo make configurable
  }
}