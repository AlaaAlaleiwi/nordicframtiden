package com.nordicframtiden.contact;

import com.nordicframtiden.settings.AppSettingsService;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Properties;

@Service
public class ContactNotificationService {

    private final AppSettingsService appSettingsService;

    public ContactNotificationService(AppSettingsService appSettingsService) {
        this.appSettingsService = appSettingsService;
    }

    public void sendNewContactRequestNotification(ContactRequest request) {
        Map<String, String> mail = appSettingsService.getMailSettings();
        if (!Boolean.parseBoolean(mail.getOrDefault("enabled", "false"))) {
            return;
        }

        String host = mail.getOrDefault("host", "").trim();
        String to = mail.getOrDefault("to", "").trim();
        if (host.isBlank() || to.isBlank()) {
            return;
        }

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(parsePort(mail.getOrDefault("port", "587")));
        sender.setUsername(mail.getOrDefault("username", "").trim());
        sender.setPassword(mail.getOrDefault("password", "").trim());

        Properties props = new Properties();
        props.put("mail.smtp.auth", !mail.getOrDefault("username", "").trim().isBlank());
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        sender.setJavaMailProperties(props);

        SimpleMailMessage message = new SimpleMailMessage();
        String from = mail.getOrDefault("from", "").trim();
        message.setFrom(from.isBlank() ? to : from);
        message.setTo(to);
        message.setSubject("New contact request: " + request.getTopic());
        message.setText(buildBody(request));

        sender.send(message);
    }

    public void sendAdminReplyNotification(ContactRequest request, String adminNote) {
        if (request == null || request.getEmail() == null || request.getEmail().isBlank()) {
            return;
        }

        String note = adminNote == null ? "" : adminNote.trim();
        if (note.isEmpty()) {
            return;
        }

        Map<String, String> mail = appSettingsService.getMailSettings();
        if (!Boolean.parseBoolean(mail.getOrDefault("enabled", "false"))) {
            return;
        }

        String host = mail.getOrDefault("host", "").trim();
        String from = mail.getOrDefault("from", "").trim();
        String recipient = request.getEmail().trim();
        if (host.isBlank() || recipient.isBlank()) {
            return;
        }

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(parsePort(mail.getOrDefault("port", "587")));
        sender.setUsername(mail.getOrDefault("username", "").trim());
        sender.setPassword(mail.getOrDefault("password", "").trim());

        Properties props = new Properties();
        props.put("mail.smtp.auth", !mail.getOrDefault("username", "").trim().isBlank());
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        sender.setJavaMailProperties(props);

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

    private int parsePort(String value) {
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
