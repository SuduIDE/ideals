package org.rri.server.commands;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.rri.server.LspContext;
import org.rri.server.LspPath;

final public class ExecutorContext {
    private final PsiFile file;
    private final Project project;
    private final LspPath path;
    private final LspContext context; // Maybe not needed
    private final CancelChecker cancelToken;

    public ExecutorContext(PsiFile file, Project project, LspPath path, LspContext context, CancelChecker cancelToken) {
        this.file = file;
        this.project = project;
        this.path = path;
        this.context = context;
        this.cancelToken = cancelToken;
    }

    public PsiFile getPsiFile() {
        return file;
    }

    public Project getProject() {
        return project;
    }

    public CancelChecker getCancelToken() {
        return cancelToken;
    }

    @SuppressWarnings("unused")
    public LspPath getPath() {
        return path;
    }

    @SuppressWarnings("unused")
    public LspContext getContext() {
        return context;
    }
}
