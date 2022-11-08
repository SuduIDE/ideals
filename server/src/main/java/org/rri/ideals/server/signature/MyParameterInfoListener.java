package org.rri.ideals.server.signature;

import com.intellij.codeInsight.hint.ParameterInfoControllerBase;
import com.intellij.codeInsight.hint.ParameterInfoListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

public class MyParameterInfoListener implements ParameterInfoListener {
  private static final Logger LOG = Logger.getInstance(MyParameterInfoListener.class);
  private final AtomicReference<ParameterInfoControllerBase.Model> currentResultRef = new AtomicReference<>();
  @Override
  public void hintUpdated(ParameterInfoControllerBase.@NotNull Model result) {
    LOG.info("parameter info set");
    currentResultRef.set(result);
  }

  public AtomicReference<ParameterInfoControllerBase.Model> getCurrentResultRef() {
    return currentResultRef;
  }

  @Override
  public void hintHidden(@NotNull Project project) {
    LOG.info("parameter info delete");
    currentResultRef.set(null);
  }
}
