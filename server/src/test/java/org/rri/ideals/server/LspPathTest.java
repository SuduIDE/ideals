package org.rri.ideals.server;

import org.junit.Test;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LspPathTest {
  @Test
  public void uriNormalization_Slashes() {
    final var expected = "file:///e:/Program Files/test.txt";

    assertAll(
        Stream.of(
            LspPath.fromLspUri("file:/e:/Program Files/test.txt").toLspUri(),
            LspPath.fromLspUri("file://e:\\Program Files\\test.txt").toLspUri(),
            LspPath.fromLspUri("file:///e:/Program%20Files/test.txt").toLspUri()
        ).map(it -> () -> assertEquals(expected, it))
    );
  }

  @Test
  public void uriNormalization_WithAlternativeSchemas() {
    assertEquals("jar:///e:/Program Files/lib.jar!/test.txt", LspPath.fromLspUri("jar:/e:/Program Files/lib.jar!/test.txt").toLspUri());
    assertEquals("git+ssh5:///e:/Program Files/lib.jar!/test.txt", LspPath.fromLspUri("git+ssh5:/e:/Program Files/lib.jar!/test.txt").toLspUri());
  }

  @Test
  public void uriNormalization_driveLetterInLowerCase() {
    assertEquals("file:///e:/Program Files/test.txt", LspPath.fromLspUri("file:///E:/Program Files/test.txt").toLspUri());
    assertEquals("jar:///e:/Program Files/lib/jar!/test.txt", LspPath.fromLspUri("jar:///E:/Program Files/lib/jar!/test.txt").toLspUri());
  }
}
