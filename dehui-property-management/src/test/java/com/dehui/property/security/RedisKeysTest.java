package com.dehui.property.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RedisKeysTest {

    @Test
    void buildsAuthAndWechatKeys() {
        assertEquals("dehui:auth:token:t-1", RedisKeys.authToken("t-1"));
        assertEquals("dehui:auth:captcha:13800000000", RedisKeys.captcha("13800000000"));
        assertEquals("dehui:wechat:session:openid-1", RedisKeys.wechatSession("openid-1"));
    }

    @Test
    void buildsPermissionDashboardRateLimitAndLockKeys() {
        assertEquals("dehui:permission:user:9", RedisKeys.userPermission(9L));
        assertEquals("dehui:dashboard:stats:12", RedisKeys.dashboardStats(12L));
        assertEquals("dehui:rate-limit:127.0.0.1:/api/mobile/auth/me", RedisKeys.rateLimit("127.0.0.1", "/api/mobile/auth/me"));
        assertEquals("dehui:lock:bill:88", RedisKeys.lock("bill", "88"));
    }
}
