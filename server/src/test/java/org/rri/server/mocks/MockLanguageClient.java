package org.rri.server.mocks;

import com.intellij.openapi.diagnostic.Logger;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.rri.server.MyLanguageClient;
import org.rri.server.TestUtil;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class MockLanguageClient implements MyLanguageClient {
  @SuppressWarnings("unused")
  private static final Logger LOG = Logger.getInstance(MockLanguageClient.class);
  @Override
  public void notifyIndexStarted() {

  }

  @Override
  public void notifyIndexFinished() {

  }

  @Override
  public void telemetryEvent(Object object) {

  }

  private final AtomicReference<CompletableFuture<PublishDiagnosticsParams>> diagnosticsFuture = new AtomicReference<>();


  @Override
  public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
    Optional.of(diagnosticsFuture.get()).ifPresent(it -> it.complete(diagnostics));
  }

  @SuppressWarnings("unused")
  public void resetDiagnosticsResult() {
    diagnosticsFuture.set(new CompletableFuture<>());
  }

  public PublishDiagnosticsParams waitAndGetDiagnosticsPublished() {
    return TestUtil.getNonBlockingEdt(
            diagnosticsFuture.accumulateAndGet(null, (current, given) -> (current == null) ?
                    new CompletableFuture<>() :
                    current
            )
    );
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
