package org.rri.server.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.psi.PsiFile;
import org.codehaus.plexus.util.ExceptionUtils;
import org.rri.server.MyTextDocumentService;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Function;

public class EditorUtil {
    private static final Logger LOG = Logger.getInstance(MyTextDocumentService.class);

    public static void withEditor(Disposable context, PsiFile file, int offset, Consumer<Editor> callback) {
        Editor editor = MiscUtil.createEditor(context, file, offset);

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
