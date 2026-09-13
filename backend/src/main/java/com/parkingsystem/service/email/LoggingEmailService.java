package com.parkingsystem.service.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Default email "provider" for local development: logs the verification code
 * to the console instead of sending a real email. Active whenever
 * app.email.mode is unset or set to LOG. Never used unless explicitly
 * selected — production deployments must set app.email.mode=SMTP.
 */
@Service
@ConditionalOnProperty(name = "app.email.mode", havingValue = "LOG", matchIfMissing = true)
@Slf4j
public class LoggingEmailService implements EmailService {

    @Override
    public void sendVerificationCode(String toEmail, String code) {
        log.info("==================================================================");
        log.info(" [DEV EMAIL] Verification code for {}: {}", toEmail, code);
        log.info(" (Set app.email.mode=SMTP with real credentials to send actual emails.)");
        log.info("==================================================================");
    }
}
