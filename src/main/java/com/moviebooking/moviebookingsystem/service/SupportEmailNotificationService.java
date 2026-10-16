package com.moviebooking.moviebookingsystem.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class SupportEmailNotificationService {

    private final JavaMailSender mailSender;

    @Value("${app.support.email-notifications-enabled:true}")
    private boolean enabled;

    public SupportEmailNotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendTicketUpdate(String recipient, String subject, String body) {
        if (!enabled || recipient == null || recipient.isBlank()) {
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipient.trim());
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
