package org.rri.server.symbol.provider;

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

public class KtDocumentSymbolProvider extends DocumentSymbolProvider{
  @Override
  public @Nullable SymbolKind symbolKind(@NotNull PsiElement elem) {
    if (elem instanceof final KtClass elemClass) {
      if (elemClass instanceof KtEnumEntry) {
        return SymbolKind.EnumMember;
      } else if (elemClass.isInterface()) {
        return SymbolKind.Interface;
      } else if (elemClass.isEnum()) {
        return SymbolKind.Enum;
      } else {
        return SymbolKind.Class;
      }
    } else if (elem instanceof KtConstructor<?>) {
      return SymbolKind.Constructor;
    } else if (elem instanceof final KtFunction elemFunc) {
      if (ktIsInsideCompanion(elemFunc)) {
        return SymbolKind.Function;
      } else if (KtPsiUtilKt.containingClass((KtElement) elem) != null) {
        return SymbolKind.Method;
      } else {
        return SymbolKind.Function;
      }
    } else if (elem instanceof KtLightMethod) {
      return ((KtLightMethod) elem).getContainingClass() instanceof KtLightClassForFacade
          ? SymbolKind.Method : SymbolKind.Function;
    } else if (elem instanceof final KtProperty property) {
      if (ktIsConstant(property)) {
        return SymbolKind.Constant;
      } else if (property.isMember()) {
        return SymbolKind.Field;
      } else {
        return SymbolKind.Variable;
      }
    } else if (elem instanceof KtVariableDeclaration
        || elem instanceof KtParameter) {
      return SymbolKind.Variable;
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
    if (elt.getModifierList() == null) {
      return false;
    }
    return elt.getModifierList().getModifier(KtTokens.CONST_KEYWORD) != null;
  }

  @Override
  public @Nullable String symbolName(@NotNull PsiElement psiElement) {
    if (!(psiElement instanceof final KtElement elem)) {
      return null;
    }
    if (elem instanceof KtClass) {
      return elem.getName() == null
          ? MemberInfoUtilsKt.qualifiedClassNameForRendering((KtClass) elem) : elem.getName();
    } else if (elem instanceof KtClassInitializer) {
      return elem.getName() == null ? "<init>" : elem.getName();
    } else if (elem instanceof KtFunction) {
      return methodLabel((KtFunction) elem);
    } else if (elem instanceof KtProperty) {
      return elem.getName();
    } else if (elem instanceof KtVariableDeclaration) {
      return elem.getName();
    } else if (elem instanceof KtParameter) {
      return elem.getName();
    } else if (elem instanceof KtConstantExpression) {
      return elem.getText();
    } else if (elem instanceof KtLightMethod) {
      return methodLabel((KtLightMethod) elem);
    }
    return null;
  }

  @NotNull
  private static String methodLabel(@NotNull KtFunction method) {
    return method.getName() + method.getValueParameters().stream()
        .map(param -> param.getTypeReference() == null
            ? "<unknown>" : param.getTypeReference().getText())
        .collect(Collectors.joining(", ", "(", ")"));
  }
}
