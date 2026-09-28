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

@org.springframework.beans.factory.annotation.Value("${app.mail.publicBaseUrl:${APP_PUBLIC_BASE_URL:}}")
    private String publicBaseUrl;

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

    // =========================
    // GDPR data export (Art. 15/20): JSON attachment
    // =========================

    public boolean sendGdprExportEmail(String to, String displayName, byte[] jsonBytes) {
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
            helper.setSubject("Your data export - Nordic Framtiden");
            helper.setText("Hello " + displayName + ",\n\n"
                + "Attached is a copy of the personal data we hold about your account "
                + "(GDPR articles 15 and 20), as a JSON file.\n\n"
                + "If you did not request this export, please contact us immediately at "
                + "gdpr@nordicframtiden.se.\n\n"
                + "Kind regards,\n"
                + "Nordic Framtiden",
                true);
            helper.addAttachment("nordicframtiden-data-export.json", new org.springframework.core.io.ByteArrayResource(jsonBytes), "application/json");
            mailSender.send(mimeMessage);
            return true;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send GDPR export email", e);
        }
    }

    // =========================
    // GDPR deletion workflow notices (Art. 17)
    // =========================

    /** Sent when the deletion request is received and awaits admin review. */
    public boolean sendGdprDeletionReceivedEmail(String to, String displayName) {
        return sendSimpleGdprDeletionEmail(to, displayName,
            "We have received your request to delete your account and personal data. "
                + "An administrator will review it and confirm a deletion date. "
                + "You can withdraw the request at any time from Settings > Privacy.");
    }

    /** Sent at approval with the fixed deletion date (at least 30 days out). */
    public boolean sendGdprDeletionScheduledEmail(String to, String displayName, java.time.LocalDate date) {
        return sendSimpleGdprDeletionEmail(to, displayName,
            "Your deletion request has been approved. Your account and personal data "
                + "will be deleted on " + date + ". "
                + "You can still withdraw the request from Settings > Privacy until that date. "
                + "Schedule history used for salary and payment records is retained "
                + "according to bookkeeping rules until the deletion.");
    }

    /** Sent after the nightly job performed the deletion. */
    public boolean sendGdprDeletionCompletedEmail(String to, String displayName) {
        return sendSimpleGdprDeletionEmail(to, displayName,
            "Your account and personal data have now been deleted as requested. "
                + "Data we must keep for bookkeeping (accounting) law is retained in "
                + "an anonymised form that can no longer be linked to you.");
    }

    private boolean sendSimpleGdprDeletionEmail(String to, String displayName, String body) {
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
        message.setSubject("Your deletion request - Nordic Framtiden");
        message.setText("Hello " + displayName + ",\n\n"
            + body + "\n\nKind regards,\nNordic Framtiden");
        mailSender.send(message);
        return true;
    }

    // =========================
    // Password reset (Swedish, professional HTML)
    // =========================

    /**
     * Sends the "click the link to choose a new password" email used by both
     * the self-service reset (login page) and admin-initiated resets.
     */
    public boolean sendPasswordResetLink(String to, String username, String rawToken, long ttlMinutes) {
        // The link opens the React app's reset page on the public domain.
        // Override with APP_PUBLIC_BASE_URL if needed (e.g. testing).
        String baseUrl = publicBaseUrl.trim();
        if (baseUrl.isBlank()) {
            baseUrl = "https://nordicframtiden.se";
        }
        String link = baseUrl + "/auth/reset-password?token=" + rawToken;

        String heading = "Återställ ditt lösenord";
        String intro = "Hej " + escapeHtml(username) + ",";
        String body = "Vi har fått en förfrågan om att återställa lösenordet till ditt konto "
            + "hos <strong>Nordic Framtiden</strong>. Klicka på knappen nedan för att "
            + "välja ett nytt lösenord. Knappen fungerar i <strong>" + ttlMinutes
            + " minuter</strong> och kan bara användas en gång.";
        String cta = "Välj nytt lösenord";
        String fallback = "Fungerar knappen inte? Kopiera länken nedan och klistra in den i din webbläsare:";
        String ignore = "Har du inte begärt detta kan du lugnt ignorera mejlet – ditt nuvarande "
            + "lösenord fortsätter att fungera.";
        String signature = "Med vänliga hälsningar,<br><strong>Nordic Framtiden</strong>";

        String html = resetEmailTemplate(heading, intro, body, link, cta, fallback, ignore, signature);
        return sendHtml(to, "Återställ ditt lösenord – Nordic Framtiden", html);
    }

    /** Welcome email for newly created accounts: the person sets their own password via the link. */
    public boolean sendWelcomeEmail(String to, String fullName, String username, String rawToken, long ttlMinutes) {
        // Same link target as the reset flow — the backend-hosted Swedish page
        // accepts the invite token and lets the person choose a password.
        String baseUrl = publicBaseUrl.trim();
        if (baseUrl.isBlank()) {
            baseUrl = "https://nordicframtiden.se";
        }
        String link = baseUrl + "/auth/reset-password?token=" + rawToken;

        String heading = "Välkommen till Nordic Framtiden";
        String intro = "Hej " + escapeHtml(fullName) + ",";
        String body = "Ditt konto hos <strong>Nordic Framtiden</strong> har skapats. "
            + "Det här är ditt användarnamn: <strong>" + escapeHtml(username) + "</strong>.<br>"
            + "Klicka på knappen nedan för att välja ett eget lösenord. "
            + "Länken fungerar i <strong>" + ttlMinutes
            + " minuter</strong> och kan bara användas en gång.";
        String cta = "Skapa ditt lösenord";
        String fallback = "Fungerar knappen inte? Kopiera länken nedan och klistra in den i din webbläsare:";
        String ignore = "Har du inte förväntat dig det här mejlet kan du lugnt ignorera det – "
            + "kontakta din administratör om något ser fel ut.";
        String signature = "Med vänliga hälsningar,<br><strong>Nordic Framtiden</strong>";

        String html = resetEmailTemplate(heading, intro, body, link, cta, fallback, ignore, signature);
        return sendHtml(to, "Välkommen till Nordic Framtiden – skapa ditt lösenord", html);
    }

    /** Confirmation email sent after a password has been changed. */
    public boolean sendPasswordResetConfirmation(String to, String username) {
        String heading = "Ditt lösenord har uppdaterats";
        String intro = "Hej " + escapeHtml(username) + ",";
        String body = "Ditt lösenord hos <strong>Nordic Framtiden</strong> har nu ändrats och "
            + "den tidigare återställningslänken är ogiltig. Du kan logga in med ditt nya lösenord.";
        String securityNote = "Känner du inte igen den här ändringen? Kontakta oss omedelbart så "
            + "hjälper vi dig att säkra kontot.";
        String signature = "Med vänliga hälsningar,<br><strong>Nordic Framtiden</strong>";

        String html = confirmationTemplate(heading, intro, body, securityNote, signature);
        return sendHtml(to, "Ditt lösenord har uppdaterats – Nordic Framtiden", html);
    }

    private String resetEmailTemplate(String heading, String intro, String body, String link,
                                      String cta, String fallback, String ignore, String signature) {
        return """
            <!DOCTYPE html>
            <html lang="sv" xmlns:v="urn:schemas-microsoft-com:vml">
            <head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"></head>
            <body style="margin:0;padding:0;background-color:#f4f6f8;font-family:'Helvetica Neue',Helvetica,Arial,sans-serif;color:#1f2933;">
              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f4f6f8;padding:32px 12px;">
                <tr><td align="center">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:560px;background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 1px 4px rgba(16,24,40,0.08);">
                    <tr><td style="background-color:#0f5132;padding:24px 40px;">
                      <span style="color:#ffffff;font-size:20px;font-weight:700;letter-spacing:0.5px;">Nordic Framtiden</span>
                    </td></tr>
                    <tr><td style="padding:36px 40px 8px 40px;">
                      <h1 style="margin:0 0 18px 0;font-size:22px;line-height:1.3;color:#101828;">%s</h1>
                      <p style="margin:0 0 14px 0;font-size:15px;line-height:1.6;">%s</p>
                      <p style="margin:0 0 26px 0;font-size:15px;line-height:1.6;">%s</p>
                      <table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 auto 26px auto;"><tr><td align="center" style="border-radius:8px;background-color:#0f5132;">
                        <a href="%s" style="display:inline-block;padding:13px 30px;font-size:15px;font-weight:600;color:#ffffff;text-decoration:none;border-radius:8px;">%s</a>
                      </td></tr></table>
                      <p style="margin:0 0 6px 0;font-size:12px;color:#667085;">%s</p>
                      <p style="margin:0 0 24px 0;font-size:12px;word-break:break-all;color:#475467;">%s</p>
                      <p style="margin:0 0 8px 0;font-size:13px;color:#475467;">%s</p>
                      <p style="margin:0;font-size:14px;line-height:1.6;color:#1f2933;">%s</p>
                    </td></tr>
                    <tr><td style="padding:20px 40px;background-color:#f9fafb;border-top:1px solid #eaecf0;">
                      <p style="margin:0;font-size:11px;color:#98a2b3;">Detta är ett automatiskt mejl. Svara inte på det – mejlen övervakas inte.</p>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body></html>
            """.formatted(heading, intro, body, link, cta, fallback, link, ignore, signature);
    }

    private String confirmationTemplate(String heading, String intro, String body,
                                        String securityNote, String signature) {
        return """
            <!DOCTYPE html>
            <html lang="sv">
            <head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"></head>
            <body style="margin:0;padding:0;background-color:#f4f6f8;font-family:'Helvetica Neue',Helvetica,Arial,sans-serif;color:#1f2933;">
              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f4f6f8;padding:32px 12px;">
                <tr><td align="center">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:560px;background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 1px 4px rgba(16,24,40,0.08);">
                    <tr><td style="background-color:#0f5132;padding:24px 40px;">
                      <span style="color:#ffffff;font-size:20px;font-weight:700;letter-spacing:0.5px;">Nordic Framtiden</span>
                    </td></tr>
                    <tr><td style="padding:36px 40px 8px 40px;">
                      <h1 style="margin:0 0 18px 0;font-size:22px;line-height:1.3;color:#101828;">%s</h1>
                      <p style="margin:0 0 14px 0;font-size:15px;line-height:1.6;">%s</p>
                      <p style="margin:0 0 26px 0;font-size:15px;line-height:1.6;">%s</p>
                      <div style="border-left:3px solid #0f5132;background:#f2f7f5;padding:12px 16px;border-radius:0 8px 8px 0;font-size:13px;line-height:1.6;color:#344054;">%s</div>
                      <p style="margin:26px 0 0 0;font-size:14px;line-height:1.6;">%s</p>
                    </td></tr>
                    <tr><td style="padding:20px 40px;background-color:#f9fafb;border-top:1px solid #eaecf0;">
                      <p style="margin:0;font-size:11px;color:#98a2b3;">Detta är ett automatiskt mejl. Svara inte på det – mejlen övervakas inte.</p>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body></html>
            """.formatted(heading, intro, body, securityNote, signature);
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** Shared HTML sender; mirrors the guard clauses used by the other senders. */
    private boolean sendHtml(String to, String subject, String html) {
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
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from.isBlank() ? to : from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(mimeMessage);
            return true;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send email", e);
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
