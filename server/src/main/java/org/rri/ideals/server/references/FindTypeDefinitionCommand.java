package org.rri.ideals.server.references;

import com.intellij.codeInsight.navigation.actions.GotoTypeDeclarationAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class FindTypeDefinitionCommand extends FindDefinitionCommandBase {
  public FindTypeDefinitionCommand(@NotNull Position pos) {
    super(pos);
  }

  @Override
  protected @NotNull Supplier<@NotNull String> getMessageSupplier() {
    return () -> "TypeDefinition call";
  }

  @Override
  protected PsiElement @Nullable [] getDeclarations(Editor editor, int offset) {
    return GotoTypeDeclarationAction.findSymbolTypes(editor, offset);
  }
}
