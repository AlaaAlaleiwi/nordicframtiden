package com.nordicframtiden.settings;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class EmailServiceTest {

    @Test
    void sendsPasswordResetMessageWhenMailEnabled() {
        AppSettingsService settings = Mockito.mock(AppSettingsService.class);
        Mockito.when(settings.getMailSettings()).thenReturn(Map.of(
            "enabled", "true",
            "host", "smtp.example.com",
            "port", "587",
            "username", "user@example.com",
            "password", "secret",
            "from", "noreply@example.com",
            "to", "admin@example.com"
        ));

        JavaMailSender mailSender = Mockito.mock(JavaMailSender.class);
        EmailService service = new EmailService(mailSender, settings);

        service.sendPasswordResetEmail("employee@example.com", "demo-user", "TempPass!123");

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }
}
