package com.nordicframtiden.settings;

import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final AppSettingsService appSettingsService;

    public EmailService(JavaMailSender mailSender, AppSettingsService appSettingsService) {
        this.mailSender = mailSender;
        this.appSettingsService = appSettingsService;
    }

    public boolean sendPasswordResetEmail(String to, String username, String rawPassword) {
        Map<String, String> mail = appSettingsService.getMailSettings();
        if (!Boolean.parseBoolean(mail.getOrDefault("enabled", "false"))) {
            return false;
        }

        String host = mail.getOrDefault("host", "").trim();
        String from = mail.getOrDefault("from", "").trim();
        if (host.isBlank() || to == null || to.isBlank()) {
            return false;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from.isBlank() ? to : from);
        message.setTo(to);
        message.setSubject("Your new password");
        message.setText("Hello " + username + ",\n\n"
            + "Your password has been reset.\n"
            + "Username: " + username + "\n"
            + "Temporary password: " + rawPassword + "\n\n"
            + "Please sign in and change it immediately.");
        mailSender.send(message);
        return true;
    }

    public String buildSalaryEmailSubject(String employeeName, String monthLabel) {
        return "Salary report for " + employeeName + " - " + monthLabel;
    }

    public String buildSalaryEmailBody(String employeeName, String monthLabel) {
        return "Hello " + employeeName + ",\n\n"
            + "Your salary PDF for " + monthLabel + " is attached.\n\n"
            + "Please review the document carefully and contact the payroll team if you have any questions.\n\n"
            + "Kind regards,\n"
            + "Nordic Framtiden";
    }

    public boolean sendSalaryPdfEmail(String to, String employeeName, byte[] pdfBytes, String monthLabel) {
        Map<String, String> mail = appSettingsService.getMailSettings();
        if (!Boolean.parseBoolean(mail.getOrDefault("enabled", "false"))) {
            return false;
        }

        String host = mail.getOrDefault("host", "").trim();
        String from = mail.getOrDefault("from", "").trim();
        if (host.isBlank() || to == null || to.isBlank()) {
            return false;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
            helper.setFrom(from.isBlank() ? to : from);
            helper.setTo(to);
            helper.setSubject(buildSalaryEmailSubject(employeeName, monthLabel));
            helper.setText(buildSalaryEmailBody(employeeName, monthLabel), true);
            helper.addAttachment("salary-" + monthLabel + ".pdf", new org.springframework.core.io.ByteArrayResource(pdfBytes), "application/pdf");
            mailSender.send(mimeMessage);
            return true;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send salary PDF email", e);
        }
    }

    public boolean sendSchedulePdfEmail(String to, String employeeName, byte[] pdfBytes, String title, OffsetDateTime start, OffsetDateTime end) {
        Map<String, String> mail = appSettingsService.getMailSettings();
        if (!Boolean.parseBoolean(mail.getOrDefault("enabled", "false"))) {
            return false;
        }

        String host = mail.getOrDefault("host", "").trim();
        String from = mail.getOrDefault("from", "").trim();
        if (host.isBlank() || to == null || to.isBlank()) {
            return false;
        }

        String period = formatPeriod(start, end);
        String safeTitle = title == null || title.isBlank() ? "Schedule" : title;

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
            helper.setFrom(from.isBlank() ? to : from);
            helper.setTo(to);
            helper.setSubject("Schedule for " + employeeName + " - " + period);
            helper.setText("Hello " + employeeName + ",\n\n"
                + "Your " + safeTitle.toLowerCase() + " for " + period + " is attached.\n\n"
                + "Please review the schedule and contact the office if you have any questions.\n\n"
                + "Kind regards,\n"
                + "Nordic Framtiden",
                true);
            helper.addAttachment("schedule-" + period.replace(" ", "-") + ".pdf", new org.springframework.core.io.ByteArrayResource(pdfBytes), "application/pdf");
            mailSender.send(mimeMessage);
            return true;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send schedule PDF email", e);
        }
    }

    private String formatPeriod(OffsetDateTime start, OffsetDateTime end) {
        if (start == null && end == null) {
            return "current-period";
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String a = start == null ? "-" : start.toLocalDate().format(fmt);
        String b = end == null ? "-" : end.toLocalDate().format(fmt);
        return a.equals("-") || b.equals("-") ? a + b : a + " to " + b;
    }
}
