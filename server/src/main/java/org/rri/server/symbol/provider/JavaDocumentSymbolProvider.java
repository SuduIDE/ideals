package org.rri.server.symbol.provider;

import com.intellij.lang.jvm.JvmModifier;
import com.intellij.psi.*;
import org.eclipse.lsp4j.SymbolKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaDocumentSymbolProvider extends DocumentSymbolProvider{
  @SuppressWarnings("UnstableApiUsage")
  @Override
  public @Nullable SymbolKind symbolKind(@NotNull PsiElement elem) {
    if (elem instanceof final PsiClass elemClass) {
      if (elemClass.isAnnotationType() || elemClass.isInterface()) {
        return SymbolKind.Interface;
      } else if (elemClass.isEnum()) {
        return SymbolKind.Enum;
      } else {
        return SymbolKind.Class;
      }
    } else if (elem instanceof PsiClassInitializer) {
      return SymbolKind.Constructor;
    } else if (elem instanceof PsiMethod) {
      return ((PsiMethod) elem).isConstructor() ? SymbolKind.Constructor : SymbolKind.Method;
    } else if (elem instanceof PsiEnumConstant) {
      return SymbolKind.EnumMember;
    } else if (elem instanceof final PsiField elemField) {
      return elemField.hasModifier(JvmModifier.STATIC) && elemField.hasModifier(JvmModifier.FINAL)
          ? SymbolKind.Constant : SymbolKind.Field;
    } else if (elem instanceof PsiVariable) {
      return SymbolKind.Variable;
    }
    return null;
  }

  @Override
  @Nullable
  public String symbolName(@NotNull PsiElement elem) {
    if (elem instanceof final PsiClass elemClass) {
      if (elemClass.getName() != null) {
        return elemClass.getName();
      }
      return elemClass.getQualifiedName() == null ? "<anonymous>" : elemClass.getQualifiedName();
    } else if (elem instanceof PsiClassInitializer) {
      String name = ((PsiClassInitializer) elem).getName();
      return name == null ? "<init>" : name;
    } else if (elem instanceof PsiMethod) {
      return methodLabel((PsiMethod) elem);
    } else if (elem instanceof PsiEnumConstant) {
      return ((PsiEnumConstant) elem).getName();
    } else if (elem instanceof PsiField) {
      return ((PsiField) elem).getName();
    } else if (elem instanceof PsiVariable) {
      String name = ((PsiVariable) elem).getName();
      return name == null ? "<unknown>" : name;
    }
    return null;
  }
}
