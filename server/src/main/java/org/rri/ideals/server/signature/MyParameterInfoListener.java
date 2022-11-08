package org.rri.ideals.server.signature;

import com.intellij.codeInsight.hint.ParameterInfoControllerBase;
import com.intellij.codeInsight.hint.ParameterInfoListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.LinkedBlockingQueue;

public class MyParameterInfoListener implements ParameterInfoListener {
  private static final Logger LOG = Logger.getInstance(MyParameterInfoListener.class);
  public final LinkedBlockingQueue<ParameterInfoControllerBase.Model> queue = new LinkedBlockingQueue<>(1);
  @Override
  public void hintUpdated(ParameterInfoControllerBase.@NotNull Model result) {
    LOG.info("parameter info set");
    assert queue.isEmpty();
    queue.add(result);
  }


  @Override
  public void hintHidden(@NotNull Project project) {
    LOG.info("parameter info delete");
    queue.poll();
  }
}
