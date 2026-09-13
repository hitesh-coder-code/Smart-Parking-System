package com.parkingsystem.service.email;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Real email sending via SMTP. Only active when app.email.mode=SMTP is set,
 * which should be paired with spring.mail.* environment variables
 * (SPRING_MAIL_HOST, SPRING_MAIL_USERNAME, SPRING_MAIL_PASSWORD, etc.)
 * configured on the deployment platform. Never silently falls back to
 * "pretend it worked" — if sending fails, the exception propagates.
 */
@Service
@ConditionalOnProperty(name = "app.email.mode", havingValue = "SMTP")
@RequiredArgsConstructor
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.email.from:no-reply@parking-system.local}")
    private String fromAddress;

    @Override
    public void sendVerificationCode(String toEmail, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Your verification code");
        message.setText("Your verification code is: " + code + "\nIt expires in 15 minutes.");
        mailSender.send(message);
    }
}
