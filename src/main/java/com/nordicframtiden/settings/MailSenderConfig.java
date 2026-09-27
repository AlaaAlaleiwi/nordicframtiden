package com.nordicframtiden.settings;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Map;
import java.util.Properties;

@Configuration
public class MailSenderConfig {

    /** Defaults to true (Gmail SMTP requires STARTTLS); set MAIL_STARTTLS_ENABLE=false for plain-text dev sinks. */
    @org.springframework.beans.factory.annotation.Value("${spring.mail.properties.mail.smtp.starttls.enable:${MAIL_STARTTLS_ENABLE:true}}")
    private String starttlsEnable;

    /**
     * Implicit SSL (SMTPS). Blank means auto-detect: port 465 (e.g. One.com's
     * send.one.com) speaks TLS from the first byte, while 587 uses STARTTLS.
     * Override with MAIL_SMTP_SSL_ENABLE=true/false if a provider deviates.
     */
    @org.springframework.beans.factory.annotation.Value("${MAIL_SMTP_SSL_ENABLE:}")
    private String sslEnable;

    @Bean
    public JavaMailSender javaMailSender(AppSettingsService appSettingsService) {
        Map<String, String> mail = appSettingsService.getMailRuntimeSettings();

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        String host = mail.getOrDefault("host", "localhost");
        String port = mail.getOrDefault("port", "587");
        String username = mail.getOrDefault("username", "");
        String password = mail.getOrDefault("password", "");

        sender.setHost(host);
        sender.setPort(Integer.parseInt(port.isBlank() ? "587" : port));
        sender.setUsername(username.isBlank() ? null : username);
        sender.setPassword(password.isBlank() ? null : password);

        boolean ssl = sslEnable == null || sslEnable.isBlank()
            ? "465".equals(port.trim())
            : Boolean.parseBoolean(sslEnable);

        Properties props = new Properties();
        props.put("mail.smtp.auth", Boolean.toString(!username.isBlank() && !password.isBlank()));
        props.put("mail.smtp.ssl.enable", Boolean.toString(ssl));
        // STARTTLS only applies to plaintext connections; never enable both.
        props.put("mail.smtp.starttls.enable", ssl ? "false" : starttlsEnable);
        props.put("mail.smtp.starttls.required", ssl ? "false" : starttlsEnable);
        props.put("mail.transport.protocol", "smtp");
        sender.setJavaMailProperties(props);

        return sender;
    }
}
