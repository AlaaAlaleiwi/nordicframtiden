package com.nordicframtiden.contact;

import com.nordicframtiden.settings.AppSettingsService;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;

@Service
public class ContactNotificationService {

    /** Bounds so a wedged SMTP server can never hang an HTTP request. */
    static final String SMTP_CONNECTION_TIMEOUT_MS = "10000";
    static final String SMTP_IO_TIMEOUT_MS = "15000";

    private static final String BRAND_GREEN = "#0f5132";

    private final AppSettingsService appSettingsService;

    public ContactNotificationService(AppSettingsService appSettingsService) {
        this.appSettingsService = appSettingsService;
    }

    @Async("mailExecutor")
    public void sendNewContactRequestNotification(ContactRequest request) {
        try {
            doSendNewContactRequestNotification(request);
        } catch (Exception e) {
            // Async: nobody is waiting on this. Avoid logging message text, recipient, or stack details.
            org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ContactNotificationService.class);
            log.error("Contact notification email could not be sent: {}", e.getClass().getSimpleName());
        }
    }

    @Async("mailExecutor")
    public void sendAdminReplyNotification(ContactRequest request, String adminNote) {
        try {
            doSendAdminReplyNotification(request, adminNote);
        } catch (Exception e) {
            org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ContactNotificationService.class);
            log.error("Contact reply email could not be sent: {}", e.getClass().getSimpleName());
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

        String from = mail.getOrDefault("from", "").trim();
        String html = buildNewRequestHtml(request);
        String subject = "Ny kontaktförfrågan: " + request.getTopic() + " – " + request.getName();
        sendHtml(senderFor(mail), from.isBlank() ? to : from, to, subject, html);
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

        // The reply signature shows who actually answered (recorded by the
        // attribution logic before the notification is sent).
        String replier = request.getHandledByName();
        String html = buildReplyHtml(request, note, replier);
        String subject = "Svar på din kontaktförfrågan – Nordic Framtiden";
        sendHtml(senderFor(mail), from.isBlank() ? recipient : from, recipient, subject, html);
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

    /** Sends an HTML email; failures propagate to the async wrapper's logger. */
    private void sendHtml(JavaMailSenderImpl sender, String from, String to, String subject, String html) {
        try {
            var mimeMessage = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(mimeMessage);
        } catch (jakarta.mail.MessagingException e) {
            throw new org.springframework.mail.MailParseException(e);
        }
    }

    // ================= Templates =================

    /**
     * The reply to the person who wrote in: branded card, personal greeting,
     * the reply in a highlighted box, their original message quoted, and a
     * signature naming the person who answered. All dynamic values escaped.
     */
    static String buildReplyHtml(ContactRequest request, String replyText, String replierName) {
        String name = escapeHtml(request.getName());
        String reply = escapeHtml(replyText).replace("\n", "<br>");
        String original = escapeHtml(request.getMessage()).replace("\n", "<br>");
        String signature = (replierName == null || replierName.isBlank())
            ? "<strong>Nordic Framtiden</strong>"
            : escapeHtml(replierName) + "<br><strong>Nordic Framtiden</strong>";

        return """
            <!DOCTYPE html>
            <html lang="sv">
            <head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"></head>
            <body style="margin:0;padding:0;background-color:#f4f6f8;font-family:'Helvetica Neue',Helvetica,Arial,sans-serif;color:#1f2933;">
              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f4f6f8;padding:32px 12px;">
                <tr><td align="center">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:560px;background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 1px 4px rgba(16,24,40,0.08);">
                    <tr><td style="background-color:%s;padding:24px 40px;">
                      <span style="color:#ffffff;font-size:20px;font-weight:700;letter-spacing:0.5px;">Nordic Framtiden</span>
                    </td></tr>
                    <tr><td style="padding:36px 40px 8px 40px;">
                      <h1 style="margin:0 0 18px 0;font-size:22px;line-height:1.3;color:#101828;">Svar på din kontaktförfrågan</h1>
                      <p style="margin:0 0 14px 0;font-size:15px;line-height:1.6;">Hej %s,</p>
                      <p style="margin:0 0 20px 0;font-size:15px;line-height:1.6;">Tack för att du hörde av dig. Här är vårt svar på din förfrågan:</p>
                      <div style="border-left:3px solid %s;background:#f2f7f5;padding:14px 18px;border-radius:0 8px 8px 0;font-size:15px;line-height:1.7;color:#1f2933;">
                        %s
                      </div>
                      <p style="margin:26px 0 0 0;font-size:14px;line-height:1.6;color:#1f2933;">%s</p>
                      <div style="margin:28px 0 0 0;padding-top:18px;border-top:1px solid #eaecf0;">
                        <p style="margin:0 0 10px 0;font-size:12px;font-weight:600;color:#98a2b3;text-transform:uppercase;letter-spacing:0.5px;">Din förfrågan</p>
                        <div style="background:#f9fafb;border:1px solid #eaecf0;border-radius:8px;padding:12px 16px;font-size:13px;line-height:1.6;color:#667085;">
                          %s
                        </div>
                      </div>
                    </td></tr>
                    <tr><td style="padding:20px 40px;background-color:#f9fafb;border-top:1px solid #eaecf0;">
                      <p style="margin:0;font-size:11px;color:#98a2b3;">Detta är ett automatiskt mejl. Svara inte direkt på det – nya frågor skickas enklast via kontaktformuläret på vår webbplats.</p>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body></html>
            """.formatted(
                BRAND_GREEN, name, BRAND_GREEN, reply, signature, original);
    }

    /** Internal notification about a new request: branded card with a detail grid. */
    static String buildNewRequestHtml(ContactRequest request) {
        String type = safeCell(request.getType());
        String name = safeCell(request.getName());
        String organization = safeCell(request.getOrganization());
        String email = safeCell(request.getEmail());
        String phone = safeCell(request.getPhone());
        String topic = safeCell(request.getTopic());
        String message = escapeHtml(request.getMessage()).replace("\n", "<br>");

        return """
            <!DOCTYPE html>
            <html lang="sv">
            <head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"></head>
            <body style="margin:0;padding:0;background-color:#f4f6f8;font-family:'Helvetica Neue',Helvetica,Arial,sans-serif;color:#1f2933;">
              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f4f6f8;padding:32px 12px;">
                <tr><td align="center">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:560px;background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 1px 4px rgba(16,24,40,0.08);">
                    <tr><td style="background-color:%s;padding:24px 40px;">
                      <span style="color:#ffffff;font-size:20px;font-weight:700;letter-spacing:0.5px;">Nordic Framtiden</span>
                    </td></tr>
                    <tr><td style="padding:36px 40px 8px 40px;">
                      <h1 style="margin:0 0 6px 0;font-size:22px;line-height:1.3;color:#101828;">Ny kontaktförfrågan</h1>
                      <p style="margin:0 0 22px 0;font-size:14px;color:#667085;">En ny förfrågan har kommit in via webbplatsens kontaktformulär.</p>
                      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="font-size:14px;line-height:1.6;color:#344054;">
                        <tr><td style="padding:6px 0;width:120px;color:#667085;">Typ</td><td style="padding:6px 0;font-weight:600;">%s</td></tr>
                        <tr><td style="padding:6px 0;color:#667085;">Namn</td><td style="padding:6px 0;font-weight:600;">%s</td></tr>
                        <tr><td style="padding:6px 0;color:#667085;">Organisation</td><td style="padding:6px 0;">%s</td></tr>
                        <tr><td style="padding:6px 0;color:#667085;">E-post</td><td style="padding:6px 0;">%s</td></tr>
                        <tr><td style="padding:6px 0;color:#667085;">Telefon</td><td style="padding:6px 0;">%s</td></tr>
                        <tr><td style="padding:6px 0;color:#667085;">Ämne</td><td style="padding:6px 0;font-weight:600;">%s</td></tr>
                      </table>
                      <div style="margin:20px 0 0 0;padding-top:18px;border-top:1px solid #eaecf0;">
                        <p style="margin:0 0 10px 0;font-size:12px;font-weight:600;color:#98a2b3;text-transform:uppercase;letter-spacing:0.5px;">Meddelande</p>
                        <div style="background:#f9fafb;border:1px solid #eaecf0;border-radius:8px;padding:12px 16px;font-size:14px;line-height:1.7;color:#1f2933;">
                          %s
                        </div>
                      </div>
                      <p style="margin:22px 0 0 0;font-size:13px;line-height:1.6;color:#475467;">Svara på förfrågan via <strong>Kontakter</strong> i admindelen av appen eller webbplatsen.</p>
                    </td></tr>
                    <tr><td style="padding:20px 40px;background-color:#f9fafb;border-top:1px solid #eaecf0;">
                      <p style="margin:0;font-size:11px;color:#98a2b3;">Detta är ett automatiskt mejl från Nordic Framtiden.</p>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body></html>
            """.formatted(
                BRAND_GREEN, type, name, organization, email, phone, topic, message);
    }

    private static String safeCell(String value) {
        return escapeHtml(value == null || value.isBlank() ? "-" : value);
    }

    static String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
