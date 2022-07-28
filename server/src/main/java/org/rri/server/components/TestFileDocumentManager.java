package org.rri.server.components;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public class TestFileDocumentManager extends FileDocumentManagerImpl {
  @SuppressWarnings("unused")
  private final static Logger LOG = Logger.getInstance(TestFileDocumentManager.class);

  @Override
  public void saveAllDocuments(boolean isExplicit) {
    WriteAction.run(this::dropAllUnsavedDocuments);
    // do nothing
  }

  @Override
  public void saveDocuments(@NotNull Predicate<? super Document> filter) {
    // do nothing
  }

  @Override
  public void saveDocument(@NotNull Document document, boolean explicit) {
    // do nothing
  }
}
