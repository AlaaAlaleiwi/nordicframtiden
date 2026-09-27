package com.nordicframtiden.contact;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The contact notification must not hang the public POST /api/contact request:
 * One.com (send.one.com) speaks implicit TLS on port 465, so the SMTP
 * transport must auto-detect SSL and must always carry bounded timeouts.
 * Delivery itself runs asynchronously so the save responds immediately.
 */
class ContactNotificationServiceTest {

    private static Map<String, String> mailSettings(String port) {
        return Map.of(
            "enabled", "true",
            "host", "send.one.com",
            "port", port,
            "username", "no-reply@nordicframtiden.se",
            "password", "secret",
            "from", "no-reply@nordicframtiden.se",
            "to", "alaa.alkassar87@gmail.com"
        );
    }

    @Test
    void port465UsesImplicitSslInsteadOfStarttls() {
        Properties props = ContactNotificationService.buildMailProperties(mailSettings("465"));

        assertEquals("true", props.getProperty("mail.smtp.ssl.enable"));
        assertEquals("false", props.getProperty("mail.smtp.starttls.enable"));
        assertEquals("false", props.getProperty("mail.smtp.starttls.required"));
        assertEquals("true", props.getProperty("mail.smtp.auth"));
    }

    @Test
    void port587UsesStarttlsWithoutImplicitSsl() {
        Properties props = ContactNotificationService.buildMailProperties(mailSettings("587"));

        assertEquals("false", props.getProperty("mail.smtp.ssl.enable"));
        assertEquals("true", props.getProperty("mail.smtp.starttls.enable"));
        assertEquals("true", props.getProperty("mail.smtp.starttls.required"));
    }

    @Test
    void smtpTransportHasBoundedTimeouts() {
        Properties props = ContactNotificationService.buildMailProperties(mailSettings("465"));

        for (String key : new String[] {"mail.smtp.connectiontimeout", "mail.smtp.timeout", "mail.smtp.writetimeout"}) {
            String value = props.getProperty(key);
            assertTrue(value != null && !value.isBlank(), key + " must be set");
            assertTrue(Integer.parseInt(value) > 0, key + " must be positive");
        }
    }

    @Test
    void disabledMailNeedsNoAuth() {
        Map<String, String> mail = mailSettings("587");
        mail = new java.util.HashMap<>(mail);
        mail.put("username", "");

        Properties props = ContactNotificationService.buildMailProperties(mail);
        assertEquals("false", props.getProperty("mail.smtp.auth"));
    }

    @Test
    void newRequestNotificationIsAsyncSoTheHttpPostRespondsImmediately() throws Exception {
        assertTrue(
            ContactNotificationService.class
                .getMethod("sendNewContactRequestNotification", ContactRequest.class)
                .isAnnotationPresent(Async.class),
            "sendNewContactRequestNotification must be @Async"
        );
    }

    @Test
    void adminReplyNotificationIsAsyncToo() throws Exception {
        assertTrue(
            ContactNotificationService.class
                .getMethod("sendAdminReplyNotification", ContactRequest.class, String.class)
                .isAnnotationPresent(Async.class),
            "sendAdminReplyNotification must be @Async"
        );
    }

    @Test
    void timeoutsAreNotInfinite() {
        Properties props = ContactNotificationService.buildMailProperties(mailSettings("587"));
        assertFalse("300000".equals(props.getProperty("mail.smtp.timeout")));
    }
}
