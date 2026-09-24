package com.nordicframtiden.settings;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class AppSettingsService {

    private final AppSettingRepository repo;

    // Email delivery is configured through the deployment environment
    // (MAIL_* variables, see application.yml). The database is never
    // consulted for mail settings, so the runtime configuration has a
    // single source of truth: the server configuration.
    @Value("${spring.mail.password:}")
    private String smtpPassword = "";

    @Value("${app.mail.enabled:false}")
    private String defaultMailEnabled = "false";

    @Value("${app.mail.provider:smtp}")
    private String defaultMailProvider = "smtp";

    @Value("${spring.mail.host:}")
    private String defaultMailHost = "";

    @Value("${spring.mail.port:587}")
    private String defaultMailPort = "587";

    @Value("${spring.mail.username:}")
    private String defaultMailUsername = "";

    @Value("${app.mail.from:}")
    private String defaultMailFrom = "";

    @Value("${app.mail.to:}")
    private String defaultMailTo = "";

    public AppSettingsService(AppSettingRepository repo) {
        this.repo = repo;
    }

    public Map<String, String> getMailSettings() {
        return Map.of(
            "enabled", defaultMailEnabled == null ? "false" : defaultMailEnabled,
            "provider", defaultMailProvider == null ? "smtp" : defaultMailProvider,
            "host", defaultMailHost == null ? "" : defaultMailHost,
            "port", defaultMailPort == null ? "587" : defaultMailPort,
            "username", defaultMailUsername == null ? "" : defaultMailUsername,
            "passwordConfigured", Boolean.toString(smtpPassword != null && !smtpPassword.isBlank()),
            "from", defaultMailFrom == null ? "" : defaultMailFrom,
            "to", defaultMailTo == null ? "" : defaultMailTo
        );
    }

    /** Runtime-only settings. Never return this map from an API. */
    public Map<String, String> getMailRuntimeSettings() {
        Map<String, String> runtime = new LinkedHashMap<>(getMailSettings());
        runtime.put("password", smtpPassword == null ? "" : smtpPassword);
        return runtime;
    }

    public boolean isMailEnabled() {
        return Boolean.parseBoolean(defaultMailEnabled);
    }

    public String getMailProvider() {
        return defaultMailProvider == null ? "smtp" : defaultMailProvider;
    }

    public String getRaw(String key, String defaultValue) {
        return repo.findByKey(key)
            .map(AppSetting::getValue)
            .filter(v -> !v.isBlank())
            .orElse(defaultValue);
    }

    public record MailSettings(
        Boolean enabled,
        String provider,
        String host,
        Integer port,
        String username,
        String from,
        String to
    ) {}
}
