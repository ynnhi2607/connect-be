package com.connect.be.core.auth.security;

import com.connect.be.core.user.model.AppUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final String HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private final byte[] secret;
    private final Duration ttl;

    public JwtService(
            @Value("${app.jwt.secret:change-this-local-development-secret}") String secret,
            @Value("${app.jwt.ttl-hours:24}") long ttlHours
    ) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttl = Duration.ofHours(ttlHours);
    }

    public TokenIssue issueToken(AppUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);

        String claims = String.format(Locale.ROOT,
                "{\"sub\":\"%s\",\"name\":\"%s\",\"iat\":%d,\"exp\":%d}",
                escapeJson(user.getEmail()),
                escapeJson(user.getName()),
                now.getEpochSecond(),
                expiresAt.getEpochSecond()
        );

        String unsignedToken = encode(HEADER) + "." + encode(claims);
        String token = unsignedToken + "." + sign(unsignedToken);
        return new TokenIssue(token, expiresAt);
    }

    public Optional<JwtClaims> parseToken(String token) {
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            return Optional.empty();
        }

        String unsignedToken = parts[0] + "." + parts[1];
        if (!MessageDigest.isEqual(sign(unsignedToken).getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }

        try {
            String claims = new String(URL_DECODER.decode(parts[1]), StandardCharsets.UTF_8);
            Optional<String> subject = readStringClaim(claims, "sub");
            Optional<Long> expiresAt = readLongClaim(claims, "exp");
            if (subject.isEmpty() || expiresAt.isEmpty()
                    || Instant.now().isAfter(Instant.ofEpochSecond(expiresAt.get()))) {
                return Optional.empty();
            }
            return Optional.of(new JwtClaims(subject.get()));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private String encode(String json) {
        return URL_ENCODER.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign JWT", exception);
        }
    }

    private Optional<String> readStringClaim(String json, String name) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
                .matcher(json);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(unescapeJson(matcher.group(1)));
    }

    private Optional<Long> readLongClaim(String json, String name) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*(\\d+)")
                .matcher(json);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(matcher.group(1)));
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String unescapeJson(String value) {
        return value
                .replace("\\t", "\t")
                .replace("\\r", "\r")
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    public record TokenIssue(String token, Instant expiresAt) {
    }

    public record JwtClaims(String subject) {
    }
}
