package org.rri.ideals.server.symbol.util;

import com.intellij.icons.AllIcons;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.DeferredIcon;
import com.intellij.ui.IconManager;
import org.eclipse.lsp4j.SymbolKind;
import org.jetbrains.annotations.NotNull;
import org.rri.ideals.server.completions.util.IconUtil;

public class SymbolUtil {
  @NotNull
  public static SymbolKind getSymbolKind(@NotNull ItemPresentation presentation) {
    var parent = Disposer.newDisposable();
    try {
      Registry.get("psi.deferIconLoading").setValue(false, parent);

      var icon = presentation.getIcon(false);

      SymbolKind kind = SymbolKind.Object;
      var iconManager = IconManager.getInstance();
      if (icon == null) {
        return SymbolKind.Object;
      }
      if (icon instanceof DeferredIcon deferredIcon) {
        icon = deferredIcon.getBaseIcon();
      }
      if (IconUtil.compareIcons(icon, AllIcons.Nodes.Method) ||
          IconUtil.compareIcons(icon, AllIcons.Nodes.AbstractMethod)) {
        kind = SymbolKind.Method;
      } else if (IconUtil.compareIcons(icon, AllIcons.Nodes.Module)
          || IconUtil.compareIcons(icon, AllIcons.Nodes.IdeaModule)
          || IconUtil.compareIcons(icon, AllIcons.Nodes.JavaModule)
          || IconUtil.compareIcons(icon, AllIcons.Nodes.ModuleGroup)) {
        kind = SymbolKind.Module;
      } else if (IconUtil.compareIcons(icon, AllIcons.Nodes.Function)) {
        kind = SymbolKind.Function;
      } else if (IconUtil.compareIcons(icon, AllIcons.Nodes.Interface) ||
          IconUtil.compareIcons(icon, iconManager.tooltipOnlyIfComposite(AllIcons.Nodes.Interface))) {
        kind = SymbolKind.Interface;
      } else if (IconUtil.compareIcons(icon, AllIcons.Nodes.Type)) {
        kind = SymbolKind.TypeParameter;
      } else if (IconUtil.compareIcons(icon, AllIcons.Nodes.Property)) {
        kind = SymbolKind.Property;
      } else if (IconUtil.compareIcons(icon, AllIcons.FileTypes.Any_type)) {
        kind = SymbolKind.File;
      } else if (IconUtil.compareIcons(icon, AllIcons.Nodes.Enum)) {
        kind = SymbolKind.Enum;
      } else if (IconUtil.compareIcons(icon, AllIcons.Nodes.Variable) ||
          IconUtil.compareIcons(icon, AllIcons.Nodes.Parameter) ||
          IconUtil.compareIcons(icon, AllIcons.Nodes.NewParameter)) {
        kind = SymbolKind.Variable;
      } else if (IconUtil.compareIcons(icon, AllIcons.Nodes.Constant)) {
        kind = SymbolKind.Constant;
      } else if (
          IconUtil.compareIcons(icon, AllIcons.Nodes.Class) ||
              IconUtil.compareIcons(icon,
                  iconManager.tooltipOnlyIfComposite(AllIcons.Nodes.Class)) ||
              IconUtil.compareIcons(icon, AllIcons.Nodes.Class) ||
              IconUtil.compareIcons(icon, AllIcons.Nodes.AbstractClass)) {
        kind = SymbolKind.Class;
      } else if (IconUtil.compareIcons(icon, AllIcons.Nodes.Field)) {
        kind = SymbolKind.Field;
      }
      return kind;
    } finally {
      Disposer.dispose(parent);
    }
  }
}
