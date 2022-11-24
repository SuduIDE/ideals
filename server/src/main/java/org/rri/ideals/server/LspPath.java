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

  private LspPath(@NotNull String uri) {
    this.normalizedUri = normalizeUri(uri);
  }

  @NotNull
  public static LspPath fromLocalPath(@NotNull Path localPath) {
    return new LspPath(localPath.toUri().toString());
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

  private static final Pattern schemeRegex = Pattern.compile("^(\\w[\\w+-.]+):/+");
  /**
   * Converts URIs to have forward slashes and ensures the protocol has three slashes.
   * <p>
   * Important for testing URIs for equality across platforms.
   * <p>
   * Package visible for tests. Shall not be used directly.
   */
  @NotNull
  static String normalizeUri(@NotNull String uriString) {
    uriString = uriString.replace("\\", "/");
    uriString = StringUtil.trimTrailing(uriString, '/');

    Matcher matcher = schemeRegex.matcher(uriString);
    if(!matcher.find())
      throw new IllegalArgumentException("URI must have schema: " + uriString);

    var schemePlusColonPlusSlashes = matcher.group(0);

    // get rid of url-encoded parts like %20 etc.
    var rest = URLDecoder.decode(uriString.substring(schemePlusColonPlusSlashes.length()), StandardCharsets.UTF_8);

    // lsp-mode expects paths to match with exact case.
    // This includes the Windows drive letter if the system is Windows.
    // So, always lowercase the drive letter to avoid any differences.
    if(rest.length() > 1 && rest.charAt(1) == ':') {
      rest = Character.toString(Character.toLowerCase(rest.charAt(0))) + ':' + rest.substring(2);
    }

    return StringUtil.trimTrailing(schemePlusColonPlusSlashes, '/') + "///" + rest;
  }
}