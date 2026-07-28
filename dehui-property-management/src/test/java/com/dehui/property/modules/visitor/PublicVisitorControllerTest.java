package com.dehui.property.modules.visitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.config.FileStorageProperties;
import com.dehui.property.modules.visitor.controller.PublicVisitorController;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

class PublicVisitorControllerTest {

    @TempDir
    Path tempDir;

    private JdbcTemplate jdbcTemplate;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> values;
    private PublicVisitorController controller;
    private MockHttpServletRequest request;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        redisTemplate = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);
        when(jdbcTemplate.queryForList(
                anyString(),
                eq(Long.class),
                any(Object[].class)
        )).thenReturn(List.of(1L));
        controller = new PublicVisitorController(jdbcTemplate, redisTemplate);
        request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
    }

    @Test
    void createsPendingParkReservationWithoutBusinessRelationshipIds() {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        ApiResponse<Map<String, Object>> response = controller.create(validBody(), request);

        assertEquals(200, response.code());
        assertEquals("PENDING", response.data().get("status"));
        assertTrue(String.valueOf(response.data().get("code")).startsWith("PVR-"));
        assertFalse(response.data().containsKey("id"));
        assertFalse(response.data().containsKey("visitorPhone"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), any(Object[].class));
        assertTrue(sql.getValue().contains("?, NULL, NULL"));
        assertTrue(sql.getValue().contains("'PENDING', 'ACTIVE', NULL, NULL"));
        verify(jdbcTemplate).queryForList(
                argThat(query -> query != null && query.contains("FOR UPDATE")),
                eq(Long.class),
                any(Object[].class)
        );

        InOrder order = inOrder(values, jdbcTemplate);
        order.verify(values, times(3)).increment(anyString());
        order.verify(jdbcTemplate).update(anyString(), any(Object[].class));
    }

    @Test
    void retriesWithSameSubmissionTokenRemainIdempotentAndRateLimited() {
        ApiResponse<Map<String, Object>> first = controller.create(validBody(), request);
        ApiResponse<Map<String, Object>> retry = controller.create(validBody(), request);

        assertEquals(first.data().get("code"), retry.data().get("code"));
        verify(values, times(6)).increment(anyString());
        verify(jdbcTemplate, times(2)).update(anyString(), any(Object[].class));
        verify(jdbcTemplate, times(2)).queryForList(
                argThat(query -> query != null && query.contains("FOR UPDATE")),
                eq(Long.class),
                any(Object[].class)
        );
    }

    @Test
    void rejectsRelationshipAndUnknownFieldsBeforeDatabaseOrRedisAccess() {
        for (String forbidden : new String[]{"tenantId", "applicantMobileUserId", "sysUserId", "unknownField"}) {
            Map<String, Object> body = new LinkedHashMap<>(validBody());
            body.put(forbidden, 99L);

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> controller.create(body, request)
            );

            assertEquals(400, exception.getCode(), forbidden);
        }
        verifyNoInteractions(jdbcTemplate, redisTemplate);
    }

    @Test
    void validatesPhoneFutureTimeAndDatabaseFieldLengths() {
        Map<String, Object> invalidPhone = new LinkedHashMap<>(validBody());
        invalidPhone.put("visitorPhone", "123");
        assertEquals(400, assertThrows(
                BusinessException.class,
                () -> controller.create(invalidPhone, request)
        ).getCode());

        Map<String, Object> past = new LinkedHashMap<>(validBody());
        past.put("visitTime", LocalDateTime.now().minusMinutes(1).withNano(0).toString());
        assertEquals(400, assertThrows(
                BusinessException.class,
                () -> controller.create(past, request)
        ).getCode());

        Map<String, Object> longName = new LinkedHashMap<>(validBody());
        longName.put("visitorName", "访".repeat(65));
        assertEquals(400, assertThrows(
                BusinessException.class,
                () -> controller.create(longName, request)
        ).getCode());

        verifyNoInteractions(jdbcTemplate, redisTemplate);
    }

    @Test
    void rateLimitReturns429BeforeAnyDatabaseAccess() {
        when(values.increment(anyString())).thenReturn(11L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> controller.create(validBody(), request)
        );

        assertEquals(429, exception.getCode());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void duplicateTokenWithDifferentPayloadReturnsConflict() {
        when(jdbcTemplate.queryForList(
                anyString(),
                eq(Long.class),
                any(Object[].class)
        )).thenReturn(List.of());
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> controller.create(validBody(), request)
        );

        assertEquals(409, exception.getCode());
    }

    @Test
    void untrustedImmediatePeerCannotSpoofForwardedClientAddress() {
        request.setRemoteAddr("203.0.113.8");
        request.addHeader("X-Forwarded-For", "198.51.100.44");

        controller.create(validBody(), request);

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(values, times(3)).increment(keys.capture());
        assertTrue(keys.getAllValues().get(1).contains("ip-" + sha256("203.0.113.8")));
        assertFalse(keys.getAllValues().get(1).contains(sha256("198.51.100.44")));
    }

    @Test
    void configuredTrustedProxyChainUsesNearestUntrustedClientAndIgnoresSpoofedPrefix() {
        controller = new PublicVisitorController(
                jdbcTemplate,
                redisTemplate,
                "127.0.0.1,10.0.0.2"
        );
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "192.0.2.99, 198.51.100.44, 10.0.0.2");

        controller.create(validBody(), request);

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(values, times(3)).increment(keys.capture());
        assertTrue(keys.getAllValues().get(1).contains("ip-" + sha256("198.51.100.44")));
        assertFalse(keys.getAllValues().get(1).contains(sha256("192.0.2.99")));
    }

    @Test
    void historyOnlyReturnsAnonymousReservationsCoveredByDeviceCredentials() {
        String token = "public-visitor-history-0001";
        String code = "PVR-" + sha256(token).substring(0, 32).toUpperCase();
        when(jdbcTemplate.queryForList(
                argThat(sql -> sql != null && sql.contains("tenant_id IS NULL")
                        && sql.contains("applicant_mobile_user_id IS NULL")),
                any(Object[].class)
        )).thenReturn(List.of(Map.of(
                "code", code,
                "visitorName", "张三",
                "visitorPhone", "138****0000",
                "status", "PENDING"
        )));

        ApiResponse<Map<String, Object>> response = controller.history(Map.of(
                "appointments", List.of(Map.of(
                        "code", code,
                        "submissionToken", token
                ))
        ));

        assertEquals(200, response.code());
        assertEquals(1, response.data().get("total"));
        assertEquals(1, ((List<?>) response.data().get("records")).size());
    }

    @Test
    void historyRejectsTamperedCodeBeforeDatabaseLookup() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> controller.history(Map.of(
                        "appointments", List.of(Map.of(
                                "code", "PVR-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                                "submissionToken", "public-visitor-history-0002"
                        ))
                ))
        );

        assertEquals(404, exception.getCode());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void qrDownloadRequiresMatchingCapabilityAndBoundInternalFile() throws Exception {
        String token = "public-visitor-qr-token-0001";
        String code = "PVR-" + sha256(token).substring(0, 32).toUpperCase();
        Path qr = tempDir.resolve("private/visitor/test-qr.png");
        Files.createDirectories(qr.getParent());
        Files.write(qr, new byte[]{1, 2, 3, 4});
        controller = new PublicVisitorController(
                jdbcTemplate,
                redisTemplate,
                "127.0.0.1",
                new FileStorageProperties(tempDir.toString())
        );
        when(jdbcTemplate.queryForList(
                argThat(sql -> sql != null && sql.contains("f.biz_type = 'VISITOR'")),
                eq(code)
        )).thenReturn(List.of(Map.of(
                "storagePath", "private/visitor/test-qr.png",
                "originalName", "test-qr.png",
                "mimeType", "image/png"
        )));

        ResponseEntity<Resource> response = controller.qrCode(code, token);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("image/png", response.getHeaders().getContentType().toString());
        assertTrue(response.getBody().exists());
        assertEquals("no-store", response.getHeaders().getCacheControl());
    }

    @Test
    void qrDownloadRejectsMismatchedTokenWithoutDatabaseLookup() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> controller.qrCode(
                        "PVR-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                        "public-visitor-qr-token-0002"
                )
        );

        assertEquals(404, exception.getCode());
        verifyNoInteractions(jdbcTemplate);
    }

    private Map<String, Object> validBody() {
        return Map.of(
                "visitorName", "张三",
                "visitorPhone", "+86 13800000000",
                "visitTime", LocalDateTime.now().plusDays(1).withNano(0).toString(),
                "visitCompany", "德汇创新中心",
                "visitReason", "商务拜访",
                "carPlateNo", "鲁A12345",
                "remark", "到访后联系前台",
                "submissionToken", "public-visitor-token-0001"
        );
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
