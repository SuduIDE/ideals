package org.rri.server;

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
            LspPath.normalizeUri("file:/e:/Program Files/test.txt"),
            LspPath.normalizeUri("file://e:\\Program Files\\test.txt"),
            LspPath.normalizeUri("file:///e:/Program Files/test.txt")
        ).map(it -> () -> assertEquals(expected, it))
    );
  }

  @Test
  public void uriNormalization_driveLetterInLowerCase() {
    assertEquals("file:///e:/Program Files/test.txt", LspPath.normalizeUri("file:///E:/Program Files/test.txt"));
  }
}
