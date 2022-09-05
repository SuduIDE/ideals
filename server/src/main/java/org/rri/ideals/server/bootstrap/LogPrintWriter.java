package org.rri.ideals.server.bootstrap;

import com.intellij.openapi.diagnostic.LogLevel;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.PrintWriter;
import java.io.StringWriter;

class LogPrintWriter extends PrintWriter {
  private final @NotNull Logger log;
  private final @NotNull LogLevel severity;

  public LogPrintWriter(@NotNull Logger log, @NotNull LogLevel severity) {
    super(new StringWriter());

    this.log = log;
    this.severity = severity;
  }

  @NotNull
  private StringWriter writer() {
    return (StringWriter) out;
  }

  @Override
  public void flush() {
    var buf = writer().getBuffer().toString();

    switch (severity) {
      case WARNING: log.warn(buf); break;
      case ERROR: log.error(buf); break;
      case DEBUG: log.debug(buf); break;
      case TRACE: log.trace(buf); break;
      default: log.info(buf);
    }

    super.flush();
  }
}


