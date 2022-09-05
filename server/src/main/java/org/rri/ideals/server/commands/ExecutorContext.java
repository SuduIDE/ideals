package org.rri.ideals.server.commands;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final public class ExecutorContext {

  @NotNull
  private final PsiFile file;
  @NotNull
  private final Project project;
  @Nullable
  private final CancelChecker cancelToken;

  public ExecutorContext(@NotNull PsiFile file, @NotNull Project project, @Nullable CancelChecker cancelToken) {
    this.file = file;
    this.project = project;
    this.cancelToken = cancelToken;
  }

  public @NotNull PsiFile getPsiFile() {
    return file;
  }

  public @NotNull Project getProject() {
    return project;
  }

  public @Nullable CancelChecker getCancelToken() {
    return cancelToken;
  }
}
