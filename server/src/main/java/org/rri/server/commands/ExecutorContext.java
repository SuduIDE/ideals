package org.rri.server.commands;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.rri.server.LspContext;
import org.rri.server.LspPath;

final public class ExecutorContext {
    private final PsiFile file;
    private final Project project;
    private final LspPath path;
    private final LspContext context; // Maybe not needed

    public ExecutorContext(PsiFile file, Project project, LspPath path, LspContext context) {
        this.file = file;
        this.project = project;
        this.path = path;
        this.context = context;
    }

    public PsiFile getPsiFile() {
        return file;
    }

    public LspPath getLspPath() {
        return path;
    }

    public Project getProject() {
        return project;
    }

    public LspContext getContext() {
        return context;
    }
}
