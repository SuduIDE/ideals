package org.rri.server.definition;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.Position;
import org.rri.server.LspContext;
import org.rri.server.LspPath;

final public class ExecutorContext {
//    private final PsiFile file;
    private final Project project;
    private final LspPath path;
    private final LspContext context; // Maybe not needed

    public ExecutorContext(Project project, LspPath path, LspContext context) {
        this.project = project;
        this.path = path;
        this.context = context;
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
