package org.rri.server.definition;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.Position;
import org.rri.server.LspPath;

final public class ExecutorContext {
//    private final PsiFile file;
    private final Project project;
    private final LspPath path;

    public ExecutorContext(Project project, LspPath path) {
        this.project = project;
        this.path = path;
    }

    public LspPath getLspPath() {
        return path;
    }

    public Project getProject() {
        return project;
    }
}
