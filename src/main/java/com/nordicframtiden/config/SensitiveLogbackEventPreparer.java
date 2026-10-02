package com.nordicframtiden.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.Marker;

/** Scrubs argument strings before Logback formats a message or passes it to appenders. */
public final class SensitiveLogbackEventPreparer extends TurboFilter {
  @Override
  public FilterReply decide(Marker marker, Logger logger, Level level, String format,
      Object[] params, Throwable throwable) {
    if (params != null) {
      for (int index = 0; index < params.length; index++) {
        if (params[index] instanceof CharSequence text) {
          params[index] = SensitiveLogSanitizer.sanitizeArgument(format, index, text.toString());
        }
      }
    }
    return FilterReply.NEUTRAL;
  }
}
