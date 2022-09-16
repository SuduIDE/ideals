package org.rri.ideals.server.references;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.navigation.ImplementationSearcher;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class FindImplementationCommand extends FindDefinitionCommandBase {
  public FindImplementationCommand(@NotNull Position pos) {
    super(pos);
  }

  @Override
  protected @NotNull Supplier<@NotNull String> getMessageSupplier() {
    return () -> "Implementation call";
  }

  @Override
  protected PsiElement @Nullable [] getDeclarations(Editor editor, int offset) {
    final var element = TargetElementUtil.findTargetElement(editor, TargetElementUtil.getInstance().getAllAccepted());
    final var onRef = ReadAction.<Boolean, RuntimeException>compute(() ->
      TargetElementUtil.getInstance().findTargetElement(editor,
          TargetElementUtil.getInstance().getDefinitionSearchFlags()
              & Integer.reverse(TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED | TargetElementUtil.LOOKUP_ITEM_ACCEPTED),
          offset) == null);
    final var shouldIncludeSelf = ReadAction.<Boolean, RuntimeException>compute(() ->
        element == null || TargetElementUtil.getInstance().includeSelfInGotoImplementation(element)
    );
    final var includeSelf = onRef && shouldIncludeSelf;
    return new ImplementationSearcher().searchImplementations(element, editor, includeSelf, onRef);
  }
}
