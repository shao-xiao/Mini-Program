package com.dehui.property.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${dehui.security.token-ttl-hours:12}")
    private long tokenTtlHours;

    public String createToken(AuthPrincipal principal) {
        String token = UUID.randomUUID().toString().replace("-", "");
        try {
            redisTemplate.opsForValue().set(
                    RedisKeys.authToken(token),
                    objectMapper.writeValueAsString(principal),
                    Duration.ofHours(tokenTtlHours)
            );
            return token;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize auth principal", exception);
        }
    }

    public Optional<AuthPrincipal> readToken(String rawToken) {
        String token = normalize(rawToken);
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String payload = redisTemplate.opsForValue().get(RedisKeys.authToken(token));
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(payload, AuthPrincipal.class));
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    public void revoke(String rawToken) {
        String token = normalize(rawToken);
        if (token != null && !token.isBlank()) {
            redisTemplate.delete(RedisKeys.authToken(token));
        }
    }

    /**
     * Revokes every active token that currently resolves to the supplied user.
     *
     * <p>Phone-authorized WeChat identity replacement is rare, but it must
     * invalidate tokens issued before the replacement. SCAN is deliberately
     * used instead of Redis KEYS so the operation does not block Redis.</p>
     */
    public int revokeUserSessions(Long userId) {
        if (userId == null) {
            return 0;
        }
        List<String> matchedKeys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions()
                .match(RedisKeys.authToken("*"))
                .count(100)
                .build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                String payload = redisTemplate.opsForValue().get(key);
                if (belongsToUser(payload, userId)) {
                    matchedKeys.add(key);
                }
            }
        }
        if (matchedKeys.isEmpty()) {
            return 0;
        }
        Long deleted = redisTemplate.delete(matchedKeys);
        return deleted == null ? 0 : deleted.intValue();
    }

    private boolean belongsToUser(String payload, Long userId) {
        if (payload == null || payload.isBlank()) {
            return false;
        }
        try {
            AuthPrincipal principal = objectMapper.readValue(payload, AuthPrincipal.class);
            return userId.equals(principal.userId())
                    && principal.roles() != null
                    && principal.roles().contains("MOBILE_USER");
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    public String normalize(String rawToken) {
        if (rawToken == null) {
            return null;
        }
        String token = rawToken.trim();
        if (token.startsWith("Bearer ")) {
            return token.substring(7).trim();
        }
        return token;
    }
}
