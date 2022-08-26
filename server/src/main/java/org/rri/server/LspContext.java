package org.rri.server;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.eclipse.lsp4j.ClientCapabilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class LspContext {
  @NotNull
  private final ClientCapabilities clientCapabilities;

  @NotNull
  private final MyLanguageClient client;

  @NotNull
  private final Map<String, String> config = new HashMap<>();

  private static final Key<LspContext> KEY = new Key<>(LspContext.class.getCanonicalName());

  private LspContext(@NotNull MyLanguageClient client,
                     @NotNull ClientCapabilities clientCapabilities) {
    this.client = client;
    this.clientCapabilities = clientCapabilities;
  }

  public static void createContext(@NotNull Project project,
                                   @NotNull MyLanguageClient client,
                                   @NotNull ClientCapabilities clientCapabilities) {
    project.putUserData(KEY, new LspContext(client, clientCapabilities));
  }

  @NotNull
  public static LspContext getContext(@NotNull Project project) {
    final var result = project.getUserData(KEY);
    if (result == null)
      throw new IllegalStateException("LSP context hasn't been created");

    return result;
  }

  @NotNull
  public ClientCapabilities getClientCapabilities() {
    return clientCapabilities;
  }

  public @NotNull MyLanguageClient getClient() {
    return client;
  }

  @Nullable
  public String getConfigValue(@NotNull String key) {
    return config.get(key);
  }
  }
