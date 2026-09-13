package com.parkingsystem.service.email;

public interface EmailService {
    void sendVerificationCode(String toEmail, String code);
}
