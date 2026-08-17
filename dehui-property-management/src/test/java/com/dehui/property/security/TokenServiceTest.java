package com.dehui.property.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class TokenServiceTest {

    @Test
    void revokesOnlyTokensIssuedToRequestedUser() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        Cursor<String> cursor = mock(Cursor.class);
        ObjectMapper objectMapper = new ObjectMapper();
        TokenService tokenService = new TokenService(redisTemplate, objectMapper);

        when(redisTemplate.scan(any())).thenReturn(cursor);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(cursor.hasNext()).thenReturn(true, true, true, false);
        when(cursor.next()).thenReturn(
                RedisKeys.authToken("target-token"),
                RedisKeys.authToken("other-token"),
                RedisKeys.authToken("system-token")
        );
        when(values.get(RedisKeys.authToken("target-token"))).thenReturn(
                objectMapper.writeValueAsString(principal(9L))
        );
        when(values.get(RedisKeys.authToken("other-token"))).thenReturn(
                objectMapper.writeValueAsString(principal(10L))
        );
        when(values.get(RedisKeys.authToken("system-token"))).thenReturn(
                objectMapper.writeValueAsString(new AuthPrincipal(
                        9L,
                        "admin",
                        "SYSTEM",
                        null,
                        List.of("ADMIN"),
                        List.of("*")
                ))
        );
        when(redisTemplate.delete(any(Collection.class))).thenReturn(1L);

        assertEquals(1, tokenService.revokeUserSessions(9L));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> keysCaptor =
                ArgumentCaptor.forClass(Collection.class);
        verify(redisTemplate).delete(keysCaptor.capture());
        assertEquals(
                List.of(RedisKeys.authToken("target-token")),
                List.copyOf(keysCaptor.getValue())
        );
    }

    private AuthPrincipal principal(Long userId) {
        return new AuthPrincipal(
                userId,
                "mobile:" + userId,
                "PUBLIC",
                null,
                List.of("MOBILE_USER"),
                List.of()
        );
    }
}
