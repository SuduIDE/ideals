package org.rri.ideals.server.symbol;

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.newStructureView.TreeActionsOwner;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.ide.util.treeView.smartTree.TreeAction;

import java.util.HashSet;
import java.util.Set;

class TreeStructureActionsOwner implements TreeActionsOwner {
  private final Set<TreeAction> myActions = new HashSet<>();
  private final StructureViewModel myModel;

  TreeStructureActionsOwner(StructureViewModel model) {
    myModel = model;
  }

  @Override
  public void setActionActive(String name, boolean state) {
  }

  @Override
  public boolean isActionActive(String name) {
    for (final Sorter sorter : myModel.getSorters()) {
      if (sorter.getName().equals(name)) {
        if (!sorter.isVisible()) return true;
      }
    }
    for(TreeAction action: myActions) {
      if (action.getName().equals(name)) return true;
    }
    return false;
  }

  public void setActionIncluded(final TreeAction filter, final boolean selected) {
    if (selected) {
      myActions.add(filter);
    }
    else {
      myActions.remove(filter);
    }
  }
}

