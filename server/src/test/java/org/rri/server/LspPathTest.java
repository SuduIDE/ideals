package org.rri.server;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.stream.Stream;

public class LspPathTest {
  @Test
  public void testConversionFromUri() {
    final var expected = LspPath.fromLocalPath(Paths.get("e:/Program Files/test.txt"));

    Stream.of(
      LspPath.fromLspUri("file:/e:/Program Files/test.txt"),
      LspPath.fromLspUri("file://e:\\Program Files\\test.txt"),
      LspPath.fromLspUri("file:///e:/Program Files/test.txt")
    ).forEach((it) -> Assert.assertEquals(expected, it));
  }

  @Test
  public void testToLspUri() {
    Assert.assertEquals("file:///e:/Program Files/test.txt",
            LspPath.fromLocalPath(Paths.get("e:/Program Files/test.txt")).toLspUri());
  }
}
