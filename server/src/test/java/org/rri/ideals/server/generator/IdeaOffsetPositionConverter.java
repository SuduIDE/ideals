package org.rri.ideals.server.generator;

import com.intellij.openapi.project.Project;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.util.MiscUtil;

public class IdeaOffsetPositionConverter implements TestGenerator.OffsetPositionConverter{
    @NotNull final Project project;

    public IdeaOffsetPositionConverter(@NotNull Project project) {
        this.project = project;
    }


    @Override
    public @NotNull Position offsetToPosition(int offset, @NotNull String path) {
        final var file = MiscUtil.resolvePsiFile(project, LspPath.fromLspUri(path));
        if (file == null) {
            throw new RuntimeException("PsiFile is null. Path: " + path);
        }
        final var doc = MiscUtil.getDocument(file);
        if (doc == null) {
            throw new RuntimeException("Document is null. Path: " + path);
        }
        return MiscUtil.offsetToPosition(doc, offset);
    }

}
