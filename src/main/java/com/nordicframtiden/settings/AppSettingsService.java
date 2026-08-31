package com.nordicframtiden.settings;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class AppSettingsService {

    private static final String MAIL_ENABLED = "mail.enabled";
    private static final String MAIL_PROVIDER = "mail.provider";
    private static final String MAIL_HOST = "mail.host";
    private static final String MAIL_PORT = "mail.port";
    private static final String MAIL_USERNAME = "mail.username";
    private static final String MAIL_FROM = "mail.from";
    private static final String MAIL_TO = "mail.to";

    private final AppSettingRepository repo;

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
            "enabled", get(MAIL_ENABLED, defaultMailEnabled),
            "provider", get(MAIL_PROVIDER, defaultMailProvider),
            "host", get(MAIL_HOST, defaultMailHost),
            "port", get(MAIL_PORT, defaultMailPort),
            "username", get(MAIL_USERNAME, defaultMailUsername),
            "passwordConfigured", Boolean.toString(smtpPassword != null && !smtpPassword.isBlank()),
            "from", get(MAIL_FROM, defaultMailFrom),
            "to", get(MAIL_TO, defaultMailTo)
        );
    }

    /** Runtime-only settings. Never return this map from an API. */
    public Map<String, String> getMailRuntimeSettings() {
        Map<String, String> runtime = new LinkedHashMap<>(getMailSettings());
        runtime.put("password", smtpPassword == null ? "" : smtpPassword);
        return runtime;
    }

    @Transactional
    public void saveMailSettings(MailSettings settings) {
        set(MAIL_ENABLED, Boolean.toString(settings.enabled()));
        set(MAIL_PROVIDER, settings.provider() == null ? "smtp" : settings.provider());
        set(MAIL_HOST, settings.host() == null ? "" : settings.host());
        set(MAIL_PORT, settings.port() == null ? "587" : String.valueOf(settings.port()));
        set(MAIL_USERNAME, settings.username() == null ? "" : settings.username());
        set(MAIL_FROM, settings.from() == null ? "" : settings.from());
        set(MAIL_TO, settings.to() == null ? "" : settings.to());
    }

    public boolean isMailEnabled() {
        return Boolean.parseBoolean(get(MAIL_ENABLED, defaultMailEnabled));
    }

    public String getMailProvider() {
        return get(MAIL_PROVIDER, defaultMailProvider);
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

    private String get(String key, String defaultValue) {
        return repo.findByKey(key)
            .map(AppSetting::getValue)
            .filter(v -> !v.isBlank())
            .orElse(defaultValue);
    }

    private void set(String key, String value) {
        Optional<AppSetting> existing = repo.findByKey(key);
        if (existing.isPresent()) {
            existing.get().setValue(value == null ? "" : value);
            return;
        }
        repo.save(new AppSetting(key, value == null ? "" : value));
    }
}
