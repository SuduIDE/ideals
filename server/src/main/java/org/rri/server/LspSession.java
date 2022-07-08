package org.rri.server;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public interface LspSession {
  @NotNull Project getProject();
}
