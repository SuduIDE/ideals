package org.rri.server;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ProjectService {
  private final static Logger LOG = Logger.getInstance(ProjectService.class);

  private final Map<LspPath, String> projectHashes = new HashMap<>();

  @NotNull
  public static ProjectService getInstance() {
    return ApplicationManager.getApplication().getService(ProjectService.class);
  }

  @NotNull
  public Project resolveProjectFromRoot(@NotNull LspPath root) {
    // TODO: in-memory virtual files for testing have temp:/// prefix, figure out how to resolve the document from them
    // otherwise it gets confusing to have to look up the line and column being tested in the test document

    if (!Files.isDirectory(root.toPath())) {
      throw new IllegalArgumentException("Isn't a directory: " + root);
    }

    return ensureProject(root);
  }

  public void closeProject(@NotNull Project project) {
    if (projectHashes.values().remove(project.getLocationHash())) {
      LOG.info("Closing project: " + project);
      ApplicationManager.getApplication().invokeAndWait(() -> ProjectManagerEx.getInstanceEx().closeAndDispose(project));
    } else {
      LOG.warn("Closing project: Project wasn't opened by LSP server; do nothing: " + project);
    }
  }

  @NotNull
  private Project ensureProject(@NotNull LspPath projectPath) {
    var project = getProject(projectPath);
    if (project == null)
      throw new IllegalArgumentException("Couldn't find document at " + projectPath);
    if (project.isDisposed())
      throw new IllegalArgumentException("Project was already disposed: " + project);

    return project;
  }

  @Nullable
  private Project getProject(@NotNull LspPath projectPath) {

    final var mgr = ProjectManagerEx.getInstanceEx();

    final var projectHash = projectHashes.get(projectPath);
    if (projectHash != null) {
      Project project = mgr.findOpenProjectByHash(projectHash);
      if (project != null && !project.isDisposed()) {
        return project;
      } else {
        LOG.info("Cached document was disposed, reopening: " + projectPath);
      }
    }

    if (!Files.exists(projectPath.toPath())) {  // todo VirtualFile?
      LOG.warn("Project path doesn't exist: " + projectPath);
      return null;
    }

    var project = findOrLoadProject(projectPath, mgr);

    if (project != null) {
      waitUntilInitialized(project);
      cacheProject(projectPath, project);
    }

    return project;
  }

  @Nullable
  private Project findOrLoadProject(@NotNull LspPath projectPath, @NotNull ProjectManagerEx mgr) {
    return Arrays.stream(mgr.getOpenProjects())
        .filter(it -> LspPath.fromLocalPath(Paths.get(Objects.requireNonNull(it.getBasePath()))).equals(projectPath))
        .findFirst()
        .orElseGet(() -> mgr.openProject(projectPath.toPath(), new OpenProjectTask().withForceOpenInNewFrame(true)));

  }

  private void waitUntilInitialized(@NotNull Project project) {
    try {
      // Wait until the project is initialized to prevent invokeAndWait hangs
      // todo avoid
      while (!project.isInitialized()) {
        //noinspection BusyWait
        Thread.sleep(100);
      }
    } catch (InterruptedException e) {
      LOG.warn("Interrupted while waiting for project to be initialized: " + project.getBasePath(), e);
      throw new RuntimeException(e);
    }
  }

  private void cacheProject(@NotNull LspPath projectPath, Project project) {
    LOG.info("Caching project: " + projectPath);
    projectHashes.put(projectPath, project.getLocationHash());
  }
}
