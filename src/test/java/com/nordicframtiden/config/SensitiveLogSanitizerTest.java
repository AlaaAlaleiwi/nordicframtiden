package com.nordicframtiden.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class SensitiveLogSanitizerTest {
  @Test
  void masksEmailsAndLabeledUsernamesPasswordsAndTokens() {
    String sanitized = SensitiveLogSanitizer.sanitize(
        "username=alice password: secret123 token=abc authorization=Bearer test-token alice@example.com");

    assertFalse(sanitized.contains("alice"));
    assertFalse(sanitized.contains("secret123"));
    assertFalse(sanitized.contains("abc"));
    assertFalse(sanitized.contains("test-token"));
    assertFalse(sanitized.contains("example.com"));
    assertTrue(sanitized.contains("username=[REDACTED]"));
    assertTrue(sanitized.contains("password: [REDACTED]"));
  }

  @Test
  void masksSensitiveArgumentsBeforeLogbackFormatsMessage() {
    LoggerContext context = new LoggerContext();
    SensitiveLogbackEventPreparer filter = new SensitiveLogbackEventPreparer();
    filter.start();
    context.addTurboFilter(filter);
    Logger logger = context.getLogger("redaction-test");
    logger.setLevel(Level.INFO);
    logger.setAdditive(false);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.setContext(context);
    appender.start();
    logger.addAppender(appender);

    try {
      logger.info("Login username={} password={} email={}", "alice", "secret123", "alice@example.com");
      String message = appender.list.get(0).getFormattedMessage();
      assertEquals("Login username=[REDACTED] password=[REDACTED] email=[REDACTED]", message);
      assertFalse(message.contains("alice"));
      assertFalse(message.contains("secret123"));
    } finally {
      logger.detachAppender(appender);
      appender.stop();
      filter.stop();
      context.stop();
    }
  }

  @Test
  void masksUsernameWhenValueHasNoSensitiveMarkerButKeyIsSensitive() {
    assertEquals("[REDACTED]", SensitiveLogSanitizer.sanitizeArgument("userId={}", 0, "123456"));
    assertTrue(SensitiveLogSanitizer.isSensitiveKey("refresh_token"));
    assertTrue(SensitiveLogSanitizer.isSensitiveKey("user.email"));
  }
}
