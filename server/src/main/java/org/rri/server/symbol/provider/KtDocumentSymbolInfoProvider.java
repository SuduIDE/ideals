package org.rri.server.symbol.provider;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import org.eclipse.lsp4j.SymbolKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade;
import org.jetbrains.kotlin.asJava.elements.KtLightMethod;
import org.jetbrains.kotlin.idea.refactoring.memberInfo.MemberInfoUtilsKt;
import org.jetbrains.kotlin.lexer.KtTokens;
import org.jetbrains.kotlin.psi.*;
import org.jetbrains.kotlin.psi.psiUtil.KtPsiUtilKt;

import java.util.stream.Collectors;

public class KtDocumentSymbolInfoProvider extends JVMDocumentSymbolInfoProvider {
  @Override
  public @Nullable Pair<@NotNull SymbolKind, @NotNull String> symbolInfo(@NotNull PsiElement psiElement) {
    if (psiElement instanceof final KtLightMethod elemLM) {
      return getPair(() -> elemLM.getContainingClass() instanceof KtLightClassForFacade
              ? SymbolKind.Method : SymbolKind.Function,
          () -> methodLabel(elemLM));
    } else if (psiElement instanceof final KtElement elem) {
      if (elem instanceof final KtClass elemClass) {
        return getPair(() -> elemClass instanceof KtEnumEntry ? SymbolKind.EnumMember
                : elemClass.isInterface() ? SymbolKind.Interface
                : elemClass.isEnum() ? SymbolKind.Enum
                : SymbolKind.Class,
            () -> elemClass.getName() == null ? MemberInfoUtilsKt.qualifiedClassNameForRendering(elemClass)
                : elemClass.getName());
      } else if (elem instanceof KtConstructor<?>) {
        return getPair(() -> SymbolKind.Constructor,
            () -> methodLabel((KtConstructor<?>) elem));
      } else if (elem instanceof final KtFunction elemFunc) {
        return getPair(() -> ktIsInsideCompanion(elemFunc) ? SymbolKind.Function
                : KtPsiUtilKt.containingClass(elemFunc) != null ? SymbolKind.Method
                : SymbolKind.Function,
            () -> methodLabel(elemFunc));
      } else if (elem instanceof final KtProperty property) {
        return getPair(() -> ktIsConstant(property) ? SymbolKind.Constant
            : property.isMember() ? SymbolKind.Field
            : SymbolKind.Variable, elem::getName);
      } else if (elem instanceof KtVariableDeclaration || elem instanceof KtParameter) {
        return getPair(() -> SymbolKind.Variable, elem::getName);
      }
    }
    return null;
  }

  private static boolean ktIsInsideCompanion(@NotNull KtFunction elem) {
    final var objDeclaration = KtPsiUtilKt.getContainingClassOrObject(elem);
    if (objDeclaration instanceof KtObjectDeclaration) {
      return ((KtObjectDeclaration) objDeclaration).isCompanion();
    }
    return false;
  }

  private static boolean ktIsConstant(@NotNull KtProperty elt) {
    return elt.getModifierList() != null
        && elt.getModifierList().getModifier(KtTokens.CONST_KEYWORD) != null;
  }

  @NotNull
  private static String methodLabel(@NotNull KtFunction method) {
    return method.getName() + method.getValueParameters().stream()
        .map(param -> param.getTypeReference() == null
            ? "<unknown>" : param.getTypeReference().getText())
        .collect(Collectors.joining(", ", "(", ")"));
  }
}
