package org.rri.ideals.server.references;

import com.intellij.codeInsight.navigation.actions.GotoTypeDeclarationAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.util.MiscUtil;

import java.util.function.Supplier;
import java.util.stream.Stream;

public class FindTypeDefinitionCommand extends FindDefinitionCommandBase {
  public FindTypeDefinitionCommand(@NotNull Position pos) {
    super(pos);
  }

  @Override
  protected @NotNull Supplier<@NotNull String> getMessageSupplier() {
    return () -> "TypeDefinition call";
  }

  @Override
  protected @NotNull Stream<PsiElement> findDefinitions(@NotNull Editor editor, int offset) {
    return MiscUtil.streamOf(GotoTypeDeclarationAction.findSymbolTypes(editor, offset));
  }
}
