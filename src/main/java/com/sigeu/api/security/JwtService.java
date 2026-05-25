package com.sigeu.api.security;

import com.sigeu.api.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class JwtService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Pattern USERNAME_PATTERN = Pattern.compile("\"sub\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ROLE_PATTERN = Pattern.compile("\"role\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern EXP_PATTERN = Pattern.compile("\"exp\"\\s*:\\s*(\\d+)");

    private final String secret;
    private final long expirationSeconds;

    public JwtService(
            @Value("${sigeu.jwt.secret:change-this-secret-before-enforcing-auth}") String secret,
            @Value("${sigeu.jwt.expiration-seconds:86400}") long expirationSeconds
    ) {
        this.secret = secret;
        this.expirationSeconds = expirationSeconds;
    }

    public String generateToken(User user) {
        long issuedAt = Instant.now().getEpochSecond();
        long expiresAt = issuedAt + expirationSeconds;

        String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payload = "{\"sub\":\"" + escape(user.getUsername()) + "\",\"role\":\"" + escape(user.getRole())
                + "\",\"iat\":" + issuedAt + ",\"exp\":" + expiresAt + "}";

        String unsignedToken = base64Url(header) + "." + base64Url(payload);
        return unsignedToken + "." + sign(unsignedToken);
    }

    public Optional<AuthenticatedUser> validateToken(String token) {
        if (token == null || token.isBlank()) return Optional.empty();

        String[] parts = token.split("\\.");
        if (parts.length != 3) return Optional.empty();

        String unsignedToken = parts[0] + "." + parts[1];
        if (!constantTimeEquals(sign(unsignedToken), parts[2])) return Optional.empty();

        try {
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            long expiresAt = Long.parseLong(find(payload, EXP_PATTERN).orElse("0"));
            if (expiresAt < Instant.now().getEpochSecond()) return Optional.empty();

            Optional<String> username = find(payload, USERNAME_PATTERN);
            Optional<String> role = find(payload, ROLE_PATTERN);
            if (username.isEmpty() || role.isEmpty()) return Optional.empty();

            return Optional.of(new AuthenticatedUser(username.get(), role.get()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private Optional<String> find(String payload, Pattern pattern) {
        Matcher matcher = pattern.matcher(payload);
        if (!matcher.find()) return Optional.empty();
        return Optional.of(matcher.group(1));
    }

    private String sign(String unsignedToken) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(unsignedToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo firmar el token", ex);
        }
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record AuthenticatedUser(String username, String role) {}
}
