package org.rri.server.components;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public class TestDocumentManager extends FileDocumentManagerImpl {
  @SuppressWarnings("unused")
  private final static Logger LOG = Logger.getInstance(TestDocumentManager.class);

  @Override
  public void saveAllDocuments(boolean isExplicit) {
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
