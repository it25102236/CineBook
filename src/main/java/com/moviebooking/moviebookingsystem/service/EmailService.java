package com.moviebooking.moviebookingsystem.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // ================= SEND VERIFICATION CODE =================

    public void sendVerificationCode(String toEmail, String code) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(toEmail);

        message.setSubject(
                "MovieBook - Email Verification Code"
        );

        message.setText(
                "Hello,\n\n" +
                        "Thank you for signing up with MovieBook.\n\n" +
                        "Your email verification code is:\n\n" +
                        code + "\n\n" +
                        "Please enter this code to verify your email address.\n\n" +
                        "If you did not create this account, please ignore this email.\n\n" +
                        "Regards,\n" +
                        "MovieBook Team"
        );

        mailSender.send(message);
    }
}