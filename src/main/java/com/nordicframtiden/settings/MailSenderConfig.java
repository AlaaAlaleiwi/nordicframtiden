package com.nordicframtiden.settings;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Map;
import java.util.Properties;

@Configuration
public class MailSenderConfig {

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

        Properties props = new Properties();
        props.put("mail.smtp.auth", Boolean.toString(!username.isBlank() && !password.isBlank()));
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.transport.protocol", "smtp");
        sender.setJavaMailProperties(props);

        return sender;
    }
}
