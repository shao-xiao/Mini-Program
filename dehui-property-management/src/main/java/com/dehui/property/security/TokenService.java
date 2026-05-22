package com.dehui.property.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
