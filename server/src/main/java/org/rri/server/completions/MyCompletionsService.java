package org.rri.server.completions;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.List;

@Service(Service.Level.PROJECT)
final public class MyCompletionsService {
    private final Project project;
    private static final Logger LOG = Logger.getInstance(MyCompletionsService.class);

    public MyCompletionsService(Project project) {
        this.project = project;
    }

    public Either<List<CompletionItem>, CompletionList> launchCompletions(PsiFile file, CancelChecker cancelChecker) {
        return null;
    }
}
