package org.rri.server;

import org.junit.Test;

import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LspPathTest {
  @Test
  public void conversionFromUri() {
    final var expected = LspPath.fromLocalPath(Paths.get("e:/Program Files/test.txt"));

    assertAll(
            Stream.of(
                    LspPath.fromLspUri("file:/e:/Program Files/test.txt"),
                    LspPath.fromLspUri("file://e:\\Program Files\\test.txt"),
                    LspPath.fromLspUri("file:///e:/Program Files/test.txt")
            ).map(it -> () -> assertEquals(expected, it))
    );
  }

  @Test
  public void toLspUri() {
    assertEquals("file:///e:/Program Files/test.txt",
            LspPath.fromLocalPath(Paths.get("e:/Program Files/test.txt")).toLspUri());
  }
}
