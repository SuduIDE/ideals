package org.rri.ideals.server.references;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;


public class FindDefinitionCommand extends FindDefinitionCommandBase {
  public FindDefinitionCommand(@NotNull Position pos) {
    super(pos);
  }

  @Override
  protected @NotNull Supplier<@NotNull String> getMessageSupplier() {
    return () -> "Definition call";
  }

  @Override
  protected PsiElement @Nullable [] getDeclarations(Editor editor, int offset) {
    final var reference = TargetElementUtil.findReference(editor, offset);
    final var flags = TargetElementUtil.getInstance().getDefinitionSearchFlags();
    final var targetElement = TargetElementUtil.getInstance().findTargetElement(editor, flags, offset);
    final Collection<PsiElement> targetElements = targetElement != null ? List.of(targetElement)
        : reference != null ? TargetElementUtil.getInstance().getTargetCandidates(reference)
        : List.of();
    return targetElements.toArray(new PsiElement[0]);
  }
}
