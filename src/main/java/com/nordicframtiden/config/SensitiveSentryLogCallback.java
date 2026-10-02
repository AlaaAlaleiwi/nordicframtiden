package com.nordicframtiden.config;

import io.sentry.SentryLogEvent;
import io.sentry.SentryLogEventAttributeValue;
import io.sentry.SentryOptions;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Scrubs Sentry structured-log bodies and attributes before transmission. */
@Component
public final class SensitiveSentryLogCallback implements SentryOptions.Logs.BeforeSendLogCallback {
  @Override
  public SentryLogEvent execute(SentryLogEvent event) {
    if (event.getBody() != null) {
      event.setBody(SensitiveLogSanitizer.sanitize(event.getBody()));
    }
    Map<String, SentryLogEventAttributeValue> attributes = event.getAttributes();
    if (attributes != null) {
      Map<String, SentryLogEventAttributeValue> sanitized = new HashMap<>(attributes);
      sanitized.replaceAll((key, value) -> {
        if (SensitiveLogSanitizer.isSensitiveKey(key)) {
          return new SentryLogEventAttributeValue("string", SensitiveLogSanitizer.REDACTED);
        }
        Object attributeValue = value.getValue();
        return "string".equals(value.getType()) && attributeValue instanceof String text
            ? new SentryLogEventAttributeValue(value.getType(), SensitiveLogSanitizer.sanitize(text))
            : value;
      });
      event.setAttributes(sanitized);
    }
    return event;
  }
}
