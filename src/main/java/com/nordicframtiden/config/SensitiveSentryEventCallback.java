package com.nordicframtiden.config;

import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Message;
import io.sentry.protocol.SentryException;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Scrubs exception messages and request data from Sentry error events. */
@Component
public final class SensitiveSentryEventCallback implements SentryOptions.BeforeSendCallback {
  @Override
  public SentryEvent execute(SentryEvent event, Hint hint) {
    Message message = event.getMessage();
    if (message != null) {
      if (message.getMessage() != null) message.setMessage(SensitiveLogSanitizer.sanitize(message.getMessage()));
      if (message.getParams() != null) {
        message.setParams(message.getParams().stream()
            .map(SensitiveLogSanitizer::sanitize).toList());
      }
    }

    if (event.getExceptions() != null) {
      for (SentryException exception : event.getExceptions()) {
        if (exception.getValue() != null) {
          exception.setValue(SensitiveLogSanitizer.sanitize(exception.getValue()));
        }
      }
    }

    Map<String, Object> extra = event.getExtras();
    if (extra != null) {
      extra.replaceAll((key, value) -> SensitiveLogSanitizer.isSensitiveKey(key)
          ? SensitiveLogSanitizer.REDACTED : scrubObject(value));
    }

    if (event.getTags() != null) {
      event.getTags().replaceAll((key, value) -> SensitiveLogSanitizer.isSensitiveKey(key)
          ? SensitiveLogSanitizer.REDACTED : SensitiveLogSanitizer.sanitize(value));
    }

    if (event.getRequest() != null) {
      event.getRequest().setQueryString(null);
      event.getRequest().setCookies(null);
      event.getRequest().setData(null);
      if (event.getRequest().getHeaders() != null) {
        event.getRequest().getHeaders().clear();
      }
    }
    return event;
  }

  private static Object scrubObject(Object value) {
    return value instanceof String text ? SensitiveLogSanitizer.sanitize(text) : value;
  }
}
