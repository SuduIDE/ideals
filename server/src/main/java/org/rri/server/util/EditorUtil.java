package org.rri.server.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.psi.PsiFile;
import org.codehaus.plexus.util.ExceptionUtils;
import org.eclipse.lsp4j.Position;
import org.rri.server.MyTextDocumentService;

import java.util.function.Consumer;

public class EditorUtil {
    private static final Logger LOG = Logger.getInstance(MyTextDocumentService.class);

    public static void withEditor(Disposable context, PsiFile file, Position position, Consumer<Editor> callback) {
        Editor editor = MiscUtil.createEditor(context, file, position);

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
