package com.nordicframtiden.config;

import java.util.regex.Pattern;

/** Conservative defense-in-depth scrubber for sensitive values in application logs. */
public final class SensitiveLogSanitizer {
  public static final String REDACTED = "[REDACTED]";

  private static final Pattern EMAIL = Pattern.compile(
      "(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
  private static final Pattern USERNAME_ASSIGNMENT = Pattern.compile(
      "(?i)([\\\"']?\\b(?:username|user[_ -]?name|user[_ -]?id|user|login|account(?:name)?|email(?:address)?|phone|mobile|fullname|display[_ -]?name)[\\\"']?\\s*[:=]\\s*[\\\"']?)([^\\s,;\\\"'}]+)");
  private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
      "(?i)([\\\"']?\\b(?:password|passwd|pwd|passphrase|token|secret|credential|authorization|api[_-]?key|client[_-]?secret|private[_-]?key|refresh[_-]?token|access[_-]?token)[\\\"']?\\s*[:=]\\s*[\\\"']?)([^\\s,;\\\"'}]+)");
  private static final Pattern BEARER_CREDENTIAL = Pattern.compile(
      "(?i)\\b(?:bearer|basic)\\s+[A-Za-z0-9._~+/-]+=*");
  private static final Pattern JWT = Pattern.compile(
      "\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b");
  private static final Pattern SENSITIVE_KEY = Pattern.compile(
      "(?i).*(?:^|[_-])(?:username|user[_-]?name|user[_-]?id|login|email(?:address)?|phone|mobile|fullname|display[_-]?name|password|passwd|pwd|passphrase|token|secret|credential|authorization|api[_-]?key|private[_-]?key|refresh[_-]?token|access[_-]?token)(?:$|[_-]).*|^(?:username|user(?:name|id)?|login|email(?:address)?|phone|mobile|fullname|display[_-]?name|password|passwd|pwd|passphrase|token|secret|credential|authorization|clientsecret|apikey|privatekey)$");
  private static final Pattern SENSITIVE_FORMAT_SUFFIX = Pattern.compile(
      "(?i)(?:username|user[_ -]?name|user[_ -]?id|user|login|account(?:name)?|email(?:address)?|phone|mobile|fullname|display[_ -]?name|password|passwd|pwd|passphrase|token|secret|credential|authorization|api[_-]?key|client[_-]?secret|private[_-]?key|refresh[_-]?token|access[_-]?token)[\\\"']?\\s*(?:[:=]\\s*)?[\\\"']?$");

  private SensitiveLogSanitizer() {}

  public static String sanitize(String value) {
    if (value == null || value.isEmpty()) return value;
    String sanitized = EMAIL.matcher(value).replaceAll(REDACTED);
    sanitized = BEARER_CREDENTIAL.matcher(sanitized).replaceAll(REDACTED);
    sanitized = JWT.matcher(sanitized).replaceAll(REDACTED);
    sanitized = USERNAME_ASSIGNMENT.matcher(sanitized).replaceAll("$1" + REDACTED);
    return SECRET_ASSIGNMENT.matcher(sanitized).replaceAll("$1" + REDACTED);
  }

  /** True when an attribute name itself identifies a credential or account identifier. */
  public static boolean isSensitiveKey(String key) {
    return key != null && SENSITIVE_KEY.matcher(key.replace('.', '_')).matches();
  }

  /**
   * Scrubs a dynamic value when its SLF4J placeholder is labelled as an
   * account identifier or credential, even if the value itself is unmarked.
   */
  public static String sanitizeArgument(String format, int argumentIndex, String value) {
    if (value == null) return null;
    int placeholder = -1;
    int from = 0;
    for (int index = 0; index <= argumentIndex; index++) {
      placeholder = format == null ? -1 : format.indexOf("{}", from);
      if (placeholder < 0) break;
      from = placeholder + 2;
    }
    if (placeholder >= 0) {
      String context = format.substring(Math.max(0, placeholder - 64), placeholder);
      if (SENSITIVE_FORMAT_SUFFIX.matcher(context).find()) return REDACTED;
    }
    return sanitize(value);
  }
}
