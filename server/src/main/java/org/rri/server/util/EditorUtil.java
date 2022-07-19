package org.rri.server.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiFile;
import org.codehaus.plexus.util.ExceptionUtils;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public class EditorUtil {
    @NotNull
    private static final Logger LOG = Logger.getInstance(EditorUtil.class);

    @NotNull
    public static Editor createEditor(@NotNull Disposable context,
                                      @NotNull PsiFile file,
                                      @NotNull Position position) {
        Document doc = MiscUtil.getDocument(file);
        EditorFactory editorFactory = EditorFactory.getInstance();

        assert doc != null;
        Editor created = editorFactory.createEditor(doc, file.getProject());
        created.getCaretModel().moveToLogicalPosition(new LogicalPosition(position.getLine(), position.getCharacter()));

        Disposer.register(context, () -> editorFactory.releaseEditor(created));

        return created;
    }


    public static void withEditor(@NotNull Disposable context,
                                  @NotNull PsiFile file,
                                  @NotNull Position position,
                                  @NotNull Consumer<Editor> callback) {
        Editor editor = createEditor(context, file, position);

        try {
            callback.accept(editor);
        } catch (Exception e) {
            LOG.error("Exception during editor callback: " + e
                    + ExceptionUtils.getStackTrace(e));
        } finally {
            EditorFactory editorFactory = EditorFactory.getInstance();
            editorFactory.releaseEditor(editor);
        }
    }
}
