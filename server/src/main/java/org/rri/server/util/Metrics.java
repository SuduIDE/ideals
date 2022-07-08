package org.rri.server.util;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class Metrics {
  private static final Logger LOG = Logger.getInstance(Metrics.class);

  public static void run(@NotNull Supplier<@NotNull String> blockNameSupplier, @NotNull Runnable block) {
    call(blockNameSupplier, (Supplier<Void>) () -> {
      block.run();
      return null;
    });
  }

  public static <T> T call(@NotNull Supplier<@NotNull String> blockNameSupplier, @NotNull Supplier<T> block) {
    if(!LOG.isDebugEnabled()) {
      return block.get();
    }

    String prefix = blockNameSupplier.get();
    LOG.debug(prefix + ": started");
    final var start = System.nanoTime();
    try {
      var result = block.get();
      prefix += ": took ";
      return result;
    } catch (Exception e) {
      prefix += ": exceptionally took ";
      throw MiscUtil.wrap(e);
    } finally {
      var end = System.nanoTime();
      LOG.debug(prefix + ((end-start)/1_000_000) + " ms");
    }
  }
}
