package org.rri.server.mocks;

import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.rri.server.MyLanguageClient;

import java.util.concurrent.CompletableFuture;

public class MockLanguageClient implements MyLanguageClient {
  @Override
  public void notifyIndexStarted() {

  }

  @Override
  public void notifyIndexFinished() {

  }

  @Override
  public void telemetryEvent(Object object) {

  }

  @Override
  public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {

  }

  @Override
  public void showMessage(MessageParams messageParams) {

  }

  @Override
  public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
    return null;
  }

  @Override
  public void logMessage(MessageParams message) {

  }
}
