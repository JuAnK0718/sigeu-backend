package com.sigeu.api.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class PasswordService {
    private static final int BCRYPT_STRENGTH = 12;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(BCRYPT_STRENGTH);

    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) return false;
        if (isBcrypt(storedPassword)) return encoder.matches(rawPassword, storedPassword);

        return MessageDigest.isEqual(
                rawPassword.getBytes(StandardCharsets.UTF_8),
                storedPassword.getBytes(StandardCharsets.UTF_8)
        );
    }

    public boolean needsHashUpgrade(String storedPassword) {
        return !isBcrypt(storedPassword);
    }

    private boolean isBcrypt(String value) {
        return value != null && (value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$"));
    }
}
