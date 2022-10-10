package org.rri.ideals.server.references;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.nio.file.Paths;

public abstract class ReferencesCommandTestBase extends BasePlatformTestCase {
  @Override
  protected String getTestDataPath() {
    return Paths.get("test-data/references").toAbsolutePath().toString();
  }
}
