package org.rri.ideals.server.mocks;

import com.intellij.openapi.diagnostic.Logger;
import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.MyLanguageClient;
import org.rri.ideals.server.TestUtil;

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
  public CompletableFuture<Void> createProgress(WorkDoneProgressCreateParams params) {
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public void notifyProgress(ProgressParams params) {
  }

  @Override
  public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
    Optional.of(diagnosticsFuture.get()).ifPresent(it -> it.complete(diagnostics));
  }

  @SuppressWarnings("unused")
  public void resetDiagnosticsResult() {
    diagnosticsFuture.set(new CompletableFuture<>());
  }

  @NotNull
  public PublishDiagnosticsParams waitAndGetDiagnosticsPublished() {
    return TestUtil.getNonBlockingEdt(
            diagnosticsFuture.accumulateAndGet(null, (current, given) -> (current == null) ?
                    new CompletableFuture<>() :
                    current
            ),
            30000
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
