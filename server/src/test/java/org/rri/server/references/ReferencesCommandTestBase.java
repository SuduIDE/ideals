package org.rri.server.references;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.rri.server.LspPath;
import org.rri.server.TestUtil;

import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public abstract class ReferencesCommandTestBase extends BasePlatformTestCase {
  protected VirtualFile projectFile;

  @Override
  protected String getTestDataPath() {
    return Paths.get("test-data/references").toAbsolutePath().toString();
  }

  @NotNull
  protected Location location(@NotNull String uri, @NotNull Range targetRange) {
    return new Location(uri, targetRange);
  }

  @NotNull
  protected LocationLink locationLink(@NotNull String uri, @NotNull Range targetRange, @NotNull Range originalRange) {
    return new LocationLink(uri, targetRange, targetRange, originalRange);
  }

  @SuppressWarnings("SameParameterValue")
  @NotNull
  protected static Range range(int line1, int char1, int line2, int char2) {
    return new Range(new Position(line1, char1), new Position(line2, char2));
  }

  protected void check(@NotNull Set<@NotNull Location> answers, @NotNull Position pos, @NotNull LspPath path) {
    final var future = new FindUsagesCommand(pos).runAsync(getProject(), path);
    final var lst = TestUtil.getNonBlockingEdt(future, 50000);
    assertNotNull(lst);
    final var result = new HashSet<>(lst);
    assertEquals(answers, result);
  }
}
