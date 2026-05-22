package com.dehui.property.security;

public final class RedisKeys {

    private static final String PREFIX = "dehui";

    private RedisKeys() {
    }

    public static String authToken(String token) {
        return PREFIX + ":auth:token:" + token;
    }

    public static String captcha(String phone) {
        return PREFIX + ":auth:captcha:" + phone;
    }

    public static String wechatSession(String openid) {
        return PREFIX + ":wechat:session:" + openid;
    }

    public static String userPermission(Long userId) {
        return PREFIX + ":permission:user:" + userId;
    }

    public static String dashboardStats(Long tenantId) {
        return PREFIX + ":dashboard:stats:" + tenantId;
    }

    public static String rateLimit(String ip, String api) {
        return PREFIX + ":rate-limit:" + ip + ":" + api;
    }

    public static String lock(String biz, String id) {
        return PREFIX + ":lock:" + biz + ":" + id;
    }
}
