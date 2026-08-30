package com.nordicframtiden.settings;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.springframework.test.util.ReflectionTestUtils;

class AppSettingsServiceTest {

    @Test
    void publicMailSettingsNeverExposeStoredPassword() {
        AppSettingRepository repo = mock(AppSettingRepository.class);
        when(repo.findByKey("mail.password"))
            .thenReturn(Optional.of(new AppSetting("mail.password", "database-secret")));

        AppSettingsService service = new AppSettingsService(repo);

        Map<String, String> result = service.getMailSettings();

        assertFalse(result.containsKey("password"));
        assertEquals("false", result.get("passwordConfigured"));
    }

    @Test
    void runtimePasswordComesFromEnvironmentAndNeverFromDatabase() {
        AppSettingRepository repo = mock(AppSettingRepository.class);
        when(repo.findByKey("mail.password"))
            .thenReturn(Optional.of(new AppSetting("mail.password", "legacy-database-secret")));
        AppSettingsService service = new AppSettingsService(repo);
        ReflectionTestUtils.setField(service, "smtpPassword", "environment-secret");

        Map<String, String> publicSettings = service.getMailSettings();
        Map<String, String> runtimeSettings = service.getMailRuntimeSettings();

        assertFalse(publicSettings.containsKey("password"));
        assertEquals("true", publicSettings.get("passwordConfigured"));
        assertEquals("environment-secret", runtimeSettings.get("password"));
    }

    @Test
    void savingMailSettingsNeverWritesPasswordToDatabase() {
        AppSettingRepository repo = mock(AppSettingRepository.class);
        AppSettingsService service = new AppSettingsService(repo);

        service.saveMailSettings(new AppSettingsService.MailSettings(
            true, "smtp", "smtp.example.com", 587, "mailer", "from@example.com", "to@example.com"));

        verify(repo, never()).findByKey("mail.password");
    }
}
