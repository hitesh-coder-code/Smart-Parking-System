package com.parkingsystem.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * Standard RFC 6238 Time-Based One-Time Password (TOTP) utility.
 * Fully compatible with Google Authenticator, Microsoft Authenticator, and Authy.
 * Implemented using pure Java cryptography (HmacSHA1, 30-second window, 6 digits).
 */
@Component
public class TotpUtil {

    private static final String HMAC_ALGO = "HmacSHA1";
    private static final int TIME_STEP_SECONDS = 30;
    private static final int CODE_DIGITS = 6;
    private static final int DIGIT_MODULO = 1_000_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    /**
     * Generates a cryptographically random 160-bit (20-byte) Base32 secret key.
     */
    public String generateSecret() {
        byte[] buffer = new byte[20];
        RANDOM.nextBytes(buffer);
        return encodeBase32(buffer);
    }

    /**
     * Generates a standard Key URI format for QR codes:
     * otpauth://totp/SmartParking:{email}?secret={secret}&issuer=SmartParking
     */
    public String getQrUri(String secret, String email, String issuer) {
        String encodedIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8);
        String encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8);
        return String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s",
                encodedIssuer, encodedEmail, secret, encodedIssuer);
    }

    /**
     * Verifies a 6-digit TOTP code against the Base32 secret with a +/-1 time step window.
     */
    public boolean verifyCode(String base32Secret, String inputCode) {
        if (base32Secret == null || inputCode == null) return false;
        String cleanCode = inputCode.trim();
        if (cleanCode.length() != CODE_DIGITS) return false;

        long currentStep = System.currentTimeMillis() / 1000L / TIME_STEP_SECONDS;

        // Check current step and +/- 1 step for clock drift
        for (long step = currentStep - 1; step <= currentStep + 1; step++) {
            String expected = generateCodeForStep(base32Secret, step);
            if (expected.equals(cleanCode)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Generates the 6-digit TOTP code for the current instant.
     */
    public String generateCurrentCode(String base32Secret) {
        long currentStep = System.currentTimeMillis() / 1000L / TIME_STEP_SECONDS;
        return generateCodeForStep(base32Secret, currentStep);
    }

    private String generateCodeForStep(String base32Secret, long step) {
        try {
            byte[] key = decodeBase32(base32Secret);
            byte[] data = ByteBuffer.allocate(8).putLong(step).array();

            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(key, HMAC_ALGO));
            byte[] hash = mac.doFinal(data);

            // Dynamic truncation (RFC 4226 / 6238)
            int offset = hash[hash.length - 1] & 0x0F;
            long truncated = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            long code = truncated % DIGIT_MODULO;
            return String.format("%06d", code);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return "";
        }
    }

    // Base32 RFC 4648 encoding
    private static String encodeBase32(byte[] data) {
        StringBuilder sb = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int next = 0;
        int bitsLeft = 0;
        while (next < data.length) {
            buffer <<= 8;
            buffer |= (data[next++] & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                bitsLeft -= 5;
                int index = (buffer >> bitsLeft) & 0x1F;
                sb.append(BASE32_ALPHABET.charAt(index));
            }
        }
        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            sb.append(BASE32_ALPHABET.charAt(index));
        }
        return sb.toString();
    }

    // Base32 RFC 4648 decoding
    private static byte[] decodeBase32(String base32) {
        String clean = base32.toUpperCase().replaceAll("[^A-Z2-7]", "");
        byte[] bytes = new byte[clean.length() * 5 / 8];
        int buffer = 0;
        int bitsLeft = 0;
        int count = 0;
        for (char c : clean.toCharArray()) {
            int val = BASE32_ALPHABET.indexOf(c);
            if (val < 0) continue;
            buffer <<= 5;
            buffer |= val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                bytes[count++] = (byte) ((buffer >> bitsLeft) & 0xFF);
            }
        }
        return bytes;
    }
}
