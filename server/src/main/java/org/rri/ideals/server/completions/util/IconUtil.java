package org.rri.ideals.server.completions.util;

import com.intellij.ui.icons.CompositeIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Objects;

public class IconUtil {
  static public boolean compareIcons(@NotNull Icon elementIcon, @NotNull Icon standardIcon) {
    if (elementIcon.equals(standardIcon)) {
      return true;
    }
    // in all cases the first icon in CompositeIcons is actually the main icon
    while (elementIcon instanceof CompositeIcon
           && ((CompositeIcon) elementIcon).getIconCount() > 0) {
      if (Objects.requireNonNull(((CompositeIcon) elementIcon).getIcon(0)).equals(standardIcon)) {
        return true;
      }
      elementIcon = ((CompositeIcon) elementIcon).getIcon(0);
    }
    return false;
  }
}
