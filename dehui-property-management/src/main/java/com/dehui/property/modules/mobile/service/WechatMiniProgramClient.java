package com.dehui.property.modules.mobile.service;

import com.dehui.property.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class WechatMiniProgramClient {

    private static final String ACCESS_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token";
    private static final String PHONE_NUMBER_URL = "https://api.weixin.qq.com/wxa/business/getuserphonenumber";
    private static final String CODE_TO_SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String appId;
    private final String appSecret;
    private volatile CachedAccessToken cachedAccessToken;

    public WechatMiniProgramClient(
            ObjectMapper objectMapper,
            @Value("${dehui.wechat.miniprogram.app-id:}") String appId,
            @Value("${dehui.wechat.miniprogram.app-secret:}") String appSecret) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.appId = appId == null ? "" : appId.trim();
        this.appSecret = appSecret == null ? "" : appSecret.trim();
    }

    public PhoneNumber getPhoneNumber(String phoneCode) {
        if (phoneCode == null || phoneCode.isBlank()) {
            throw new BusinessException(400, "手机号授权码不能为空");
        }
        String accessToken = accessToken();
        try {
            String body = objectMapper.writeValueAsString(Map.of("code", phoneCode));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(PHONE_NUMBER_URL + "?access_token=" + encode(accessToken)))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            JsonNode response = send(request);
            assertWechatSuccess(response, "微信手机号授权失败");
            JsonNode phoneInfo = response.path("phone_info");
            String phoneNumber = phoneInfo.path("phoneNumber").asText("");
            if (phoneNumber.isBlank()) {
                throw new BusinessException(502, "微信未返回手机号");
            }
            return new PhoneNumber(
                    phoneNumber,
                    phoneInfo.path("purePhoneNumber").asText(phoneNumber),
                    phoneInfo.path("countryCode").asText("")
            );
        } catch (IOException exception) {
            throw new BusinessException(502, "微信手机号授权请求失败");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(502, "微信手机号授权请求中断");
        }
    }

    public MiniProgramSession exchangeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(400, "微信登录凭证不能为空");
        }
        assertConfigured();
        try {
            URI uri = URI.create(CODE_TO_SESSION_URL
                    + "?appid=" + encode(appId)
                    + "&secret=" + encode(appSecret)
                    + "&js_code=" + encode(code.trim())
                    + "&grant_type=authorization_code");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            JsonNode response = send(request);
            assertWechatSuccess(response, "微信登录失败");
            String openid = response.path("openid").asText("").trim();
            if (openid.isBlank()) {
                throw new BusinessException(502, "微信未返回 openid");
            }
            String unionid = response.path("unionid").asText("").trim();
            String sessionKey = response.path("session_key").asText("");
            return new MiniProgramSession(
                    appId,
                    openid,
                    unionid.isBlank() ? null : unionid,
                    sha256(sessionKey)
            );
        } catch (IOException exception) {
            throw new BusinessException(502, "微信登录请求失败");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(502, "微信登录请求中断");
        }
    }

    private String accessToken() {
        assertConfigured();
        CachedAccessToken current = cachedAccessToken;
        if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return current.token();
        }
        synchronized (this) {
            current = cachedAccessToken;
            if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
                return current.token();
            }
            try {
                URI uri = URI.create(ACCESS_TOKEN_URL
                        + "?grant_type=client_credential"
                        + "&appid=" + encode(appId)
                        + "&secret=" + encode(appSecret));
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(Duration.ofSeconds(8))
                        .GET()
                        .build();
                JsonNode response = send(request);
                assertWechatSuccess(response, "微信 access_token 获取失败");
                String token = response.path("access_token").asText("");
                if (token.isBlank()) {
                    throw new BusinessException(502, "微信未返回 access_token");
                }
                long expiresIn = Math.max(300L, response.path("expires_in").asLong(7200L) - 120L);
                cachedAccessToken = new CachedAccessToken(token, Instant.now().plusSeconds(expiresIn));
                return token;
            } catch (IOException exception) {
                throw new BusinessException(502, "微信 access_token 请求失败");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new BusinessException(502, "微信 access_token 请求中断");
            }
        }
    }

    private JsonNode send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException(502, "微信接口响应异常");
        }
        return objectMapper.readTree(response.body());
    }

    private void assertConfigured() {
        if (appId.isBlank() || appSecret.isBlank() || "change_me".equals(appId) || "change_me".equals(appSecret)) {
            throw new BusinessException(500, "微信小程序 AppID/AppSecret 未配置");
        }
    }

    private void assertWechatSuccess(JsonNode response, String fallbackMessage) {
        int errorCode = response.path("errcode").asInt(0);
        if (errorCode != 0) {
            String message = response.path("errmsg").asText(fallbackMessage);
            throw new BusinessException(502, message);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record CachedAccessToken(String token, Instant expiresAt) {
    }

    public record PhoneNumber(String phoneNumber, String purePhoneNumber, String countryCode) {
    }

    public record MiniProgramSession(String appId, String openid, String unionid, String sessionKeyHash) {
    }
}
