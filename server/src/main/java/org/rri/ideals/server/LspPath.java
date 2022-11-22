package org.rri.ideals.server;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LspPath {
  @NotNull
  private final String normalizedUri;

  private LspPath(@NotNull String uri, @Nullable String scheme) {
    this.normalizedUri = normalizeUri(uri, scheme);
  }

  private LspPath(@NotNull String uri) {
    this(uri, null);
  }

  @NotNull
  public static LspPath fromLocalPath(@NotNull Path localPath) {
    return new LspPath(localPath.toUri().toString());
  }

  @NotNull
  public static LspPath fromLocalPath(@NotNull Path localPath, @NotNull String scheme) {
    return new LspPath(localPath.toUri().toString(), scheme);
  }

  @NotNull
  public static LspPath fromLspUri(@NotNull String uri) {
    return new LspPath(uri);
  }

  @NotNull
  public static LspPath fromVirtualFile(@NotNull VirtualFile virtualFile) {
    return LspPath.fromLspUri(virtualFile.getUrl());
  }

  @NotNull
  public String toLspUri() {
    return normalizedUri.replace("%20", " ");
  }

  @NotNull
  public Path toPath() {
    try {
      return Paths.get(new URI(normalizedUri.replace(" ", "%20")));
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  @Nullable
  public VirtualFile refreshAndFindVirtualFile() {
    return VirtualFileManager.getInstance().refreshAndFindFileByUrl(normalizedUri);
  }

  @Nullable
  public VirtualFile findVirtualFile() {
    return VirtualFileManager.getInstance().findFileByUrl(normalizedUri);
  }

  @Override
  public String toString() {
    return normalizedUri;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LspPath lspPath = (LspPath) o;
    return normalizedUri.equals(lspPath.normalizedUri);
  }

  @Override
  public int hashCode() {
    return Objects.hash(normalizedUri);
  }

  private static final Pattern protocolRegex = Pattern.compile("^file:/+");
  private static final Pattern defaultSchemeRegex = Pattern.compile("file");
  private static final Pattern driveLetterRegex = Pattern.compile("file:///([A-Z]:)/.*");

  /**
   * Converts URIs to have forward slashes and ensures the protocol has three slashes.
   * <p>
   * Important for testing URIs for equality across platforms.
   * <p>
   * Package visible for tests. Shall not be used directly.
   */
  @NotNull
  static String normalizeUri(@NotNull String uri, @Nullable String scheme) {
    var decodedUri = URLDecoder.decode(uri, StandardCharsets.UTF_8);
    decodedUri = StringUtil.trimTrailing(decodedUri, '/');
    decodedUri = protocolRegex.matcher(decodedUri).replaceFirst("file:///");
    if (scheme != null) {
      decodedUri = defaultSchemeRegex.matcher(decodedUri).replaceFirst(scheme);
    }
    decodedUri = decodedUri.replace("\\", "/");

    // lsp-mode expects paths to match with exact case.
    // This includes the Windows drive letter if the system is Windows.
    // So, always lowercase the drive letter to avoid any differences.
    Matcher matcher = driveLetterRegex.matcher(decodedUri);
    if (matcher.find()) {
      var replacement = matcher.group(1).toLowerCase();
      var matchStart = matcher.start(1);
      var matchEnd = matcher.end(1);
      decodedUri = decodedUri.substring(0, matchStart) + replacement + decodedUri.substring(matchEnd);
    }

    return decodedUri;
  }

  @NotNull
  static String normalizeUri(@NotNull String uri) {
    return normalizeUri(uri, null);
  }
}