package org.rri.ideals.server.generator;

import com.intellij.openapi.project.Project;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.util.MiscUtil;

import java.util.Optional;

public class IdeaOffsetPositionConverter implements TestGenerator.OffsetPositionConverter{
    @NotNull final Project project;

    public IdeaOffsetPositionConverter(@NotNull Project project) {
        this.project = project;
    }


    @Override
    public @NotNull Position offsetToPosition(int offset, @NotNull String path) {
        final var file = Optional.ofNullable(MiscUtil.resolvePsiFile(project, LspPath.fromLspUri(path)))
                .orElseThrow(() -> new RuntimeException("PsiFile is null. Path: " + path));
        final var doc = Optional.ofNullable(MiscUtil.getDocument(file))
                .orElseThrow(() -> new RuntimeException("Document is null. Path: " + path));
        return MiscUtil.offsetToPosition(doc, offset);
    }

}
