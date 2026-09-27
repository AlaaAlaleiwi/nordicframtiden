package com.nordicframtiden.contact;

import com.nordicframtiden.settings.AppSettingsService;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Properties;

@Service
public class ContactNotificationService {

    /** Bounds so a wedged SMTP server can never hang an HTTP request. */
    static final String SMTP_CONNECTION_TIMEOUT_MS = "10000";
    static final String SMTP_IO_TIMEOUT_MS = "15000";

    private final AppSettingsService appSettingsService;

    public ContactNotificationService(AppSettingsService appSettingsService) {
        this.appSettingsService = appSettingsService;
    }

    @Async("mailExecutor")
    public void sendNewContactRequestNotification(ContactRequest request) {
        try {
            doSendNewContactRequestNotification(request);
        } catch (Exception e) {
            // Async: nobody is waiting on this. Logging is the safety net.
            org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ContactNotificationService.class);
            log.error("Notification email for contact request {} could not be sent",
                request == null ? null : request.getId(), e);
        }
    }

    @Async("mailExecutor")
    public void sendAdminReplyNotification(ContactRequest request, String adminNote) {
        try {
            doSendAdminReplyNotification(request, adminNote);
        } catch (Exception e) {
            org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ContactNotificationService.class);
            log.error("Reply email for contact request {} could not be sent",
                request == null ? null : request.getId(), e);
        }
    }

    private void doSendNewContactRequestNotification(ContactRequest request) {
        Map<String, String> mail = appSettingsService.getMailRuntimeSettings();
        if (!Boolean.parseBoolean(mail.getOrDefault("enabled", "false"))) {
            return;
        }

        String host = mail.getOrDefault("host", "").trim();
        String to = mail.getOrDefault("to", "").trim();
        if (host.isBlank() || to.isBlank()) {
            return;
        }

        JavaMailSenderImpl sender = senderFor(mail);

        SimpleMailMessage message = new SimpleMailMessage();
        String from = mail.getOrDefault("from", "").trim();
        message.setFrom(from.isBlank() ? to : from);
        message.setTo(to);
        message.setSubject("New contact request: " + request.getTopic());
        message.setText(buildBody(request));

        sender.send(message);
    }

    private void doSendAdminReplyNotification(ContactRequest request, String adminNote) {
        if (request == null || request.getEmail() == null || request.getEmail().isBlank()) {
            return;
        }

        String note = adminNote == null ? "" : adminNote.trim();
        if (note.isEmpty()) {
            return;
        }

        Map<String, String> mail = appSettingsService.getMailRuntimeSettings();
        if (!Boolean.parseBoolean(mail.getOrDefault("enabled", "false"))) {
            return;
        }

        String host = mail.getOrDefault("host", "").trim();
        String from = mail.getOrDefault("from", "").trim();
        String recipient = request.getEmail().trim();
        if (host.isBlank() || recipient.isBlank()) {
            return;
        }

        JavaMailSenderImpl sender = senderFor(mail);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from.isBlank() ? recipient : from);
        message.setTo(recipient);
        message.setSubject("Reply to your contact request");
        message.setText(
            "Hello " + request.getName() + ",\n\n"
                + "We have added a response to your contact request.\n\n"
                + "Message:\n"
                + note + "\n\n"
                + "Best regards,\n"
                + "Nordic Framtiden"
        );

        sender.send(message);
    }

    /**
     * SMTP sender mirroring MailSenderConfig: port 465 (One.com) speaks
     * implicit TLS from the first byte, 587 uses STARTTLS — never both.
     * Timeouts are always bounded so a blocked socket fails fast instead of
     * hanging the caller.
     */
    static JavaMailSenderImpl senderFor(Map<String, String> mail) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        String host = mail.getOrDefault("host", "").trim();
        String port = mail.getOrDefault("port", "587").trim();
        String username = mail.getOrDefault("username", "").trim();
        String password = mail.getOrDefault("password", "").trim();

        sender.setHost(host);
        sender.setPort(parsePort(port));
        sender.setUsername(username.isBlank() ? null : username);
        sender.setPassword(password.isBlank() ? null : password);

        sender.setJavaMailProperties(buildMailProperties(mail));
        return sender;
    }

    /** Visible for tests: SSL auto-detect (465), STARTTLS otherwise, bounded timeouts. */
    static Properties buildMailProperties(Map<String, String> mail) {
        String port = mail.getOrDefault("port", "587").trim();
        String username = mail.getOrDefault("username", "").trim();

        boolean ssl = "465".equals(port);

        Properties props = new Properties();
        props.put("mail.smtp.auth", Boolean.toString(!username.isBlank()));
        props.put("mail.smtp.ssl.enable", Boolean.toString(ssl));
        props.put("mail.smtp.starttls.enable", Boolean.toString(!ssl));
        props.put("mail.smtp.starttls.required", Boolean.toString(!ssl));
        props.put("mail.smtp.connectiontimeout", SMTP_CONNECTION_TIMEOUT_MS);
        props.put("mail.smtp.timeout", SMTP_IO_TIMEOUT_MS);
        props.put("mail.smtp.writetimeout", SMTP_IO_TIMEOUT_MS);
        props.put("mail.transport.protocol", "smtp");
        return props;
    }

    private static int parsePort(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return 587;
        }
    }

    private String buildBody(ContactRequest request) {
        return "New contact request\n\n"
            + "Type: " + request.getType() + "\n"
            + "Name: " + request.getName() + "\n"
            + "Organization: " + safe(request.getOrganization()) + "\n"
            + "Email: " + request.getEmail() + "\n"
            + "Phone: " + safe(request.getPhone()) + "\n"
            + "Topic: " + request.getTopic() + "\n\n"
            + "Message:\n" + request.getMessage();
    }

    private String safe(String value) {
        return value == null ? "-" : value;
    }
}
