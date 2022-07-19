package org.rri.server.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNameIdentifierOwner;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.server.LspPath;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class MiscUtil {
  private static final Logger LOG = Logger.getInstance(MiscUtil.class);
  private MiscUtil() {}

  @NotNull
  public static <T> T with(@NotNull T object, @NotNull Consumer<T> block) {
    block.accept(object);
    return object;
  }

  @NotNull
  public static Position offsetToPosition(@NotNull Document doc, int offset) {
    if (offset == -1) {
      return new Position(0, 0);
    }
    var line = doc.getLineNumber(offset);
    var lineStartOffset = doc.getLineStartOffset(line);
    var column = offset - lineStartOffset;
    return new Position(line, column);
  }

  @Nullable
  public static PsiFile resolvePsiFile(@NotNull Project project, @NotNull LspPath path) {
    var result = new Ref<PsiFile>();
    withPsiFileInReadAction(project, path, result::set);
    return result.get();
  }

  public static void withPsiFileInReadAction(@NotNull Project project, @NotNull LspPath path, @NotNull Consumer<@NotNull PsiFile> block) {
    final var virtualFile = path.findVirtualFile();

    if (virtualFile == null) {
      LOG.info("File not found: " + path);
      return;
    }

    ApplicationManager.getApplication().runReadAction(() -> {
      final var psiFile = PsiManager.getInstance(project).findFile(virtualFile);

      if (psiFile == null) {
        LOG.info("Unable to get PSI for virtual file: " + virtualFile);
        return;
      }

      block.accept(psiFile);
    });
  }

  @Nullable
  public static Document getDocument(@NotNull PsiFile file) {
    var virtualFile = file.getVirtualFile();

    if(virtualFile == null)
      return file.getViewProvider().getDocument();

    var doc = FileDocumentManager.getInstance() .getDocument(virtualFile);

    if (doc == null) {
      FileDocumentManagerImpl.registerDocument(
              new DocumentImpl(file.getViewProvider().getContents()),
              virtualFile);
      doc = FileDocumentManager.getInstance()
              .getDocument(virtualFile);
    }

    return doc;
  }

  @NotNull
  public static Runnable asWriteAction(@NotNull Runnable action) {
    return () -> ApplicationManager.getApplication().runWriteAction(action);
  }

  @NotNull
  public static RuntimeException wrap(@NotNull Exception e) {
    return e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
  }

  public interface RunnableWithException {
    void run() throws Exception;
  }
  public static Runnable asRunnable(@NotNull MiscUtil.RunnableWithException action) {
    return () -> {
      try {
        action.run();
      } catch (Exception e) {
        throw wrap(e);
      }
    };
  }

  public static <T> T unexceptionize(@NotNull Callable<T> block) {
    try {
      return block.call();
    } catch (Exception e) {
      throw wrap(e);
    }
  }

  @Nullable
  public static Location psiElementLocation(@NotNull PsiElement elem,
                                            @NotNull String uri,
                                            @NotNull Document doc) {
    if (elem instanceof PsiNameIdentifierOwner) {
      PsiElement identifier = ((PsiNameIdentifierOwner) elem).getNameIdentifier();
      if (identifier == null) { return null; }
      TextRange range = identifier.getTextRange();
      return new Location(uri,
              new Range(offsetToPosition(doc, range.getStartOffset()), offsetToPosition(doc, range.getEndOffset())));
    } else {
      return null;
    }
  }

  public static int positionToOffset(@NotNull Position pos,
                                     @NotNull Document doc) {
    return doc.getLineStartOffset(pos.getLine()) + pos.getCharacter();
  }
}
