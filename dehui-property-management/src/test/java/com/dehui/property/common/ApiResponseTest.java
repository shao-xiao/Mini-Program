package com.dehui.property.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void successUsesUnifiedEnvelope() {
        ApiResponse<String> response = ApiResponse.success("ok");

        assertEquals(200, response.code());
        assertEquals("操作成功", response.message());
        assertEquals("ok", response.data());
    }

    @Test
    void errorUsesUnifiedEnvelopeWithoutData() {
        ApiResponse<Void> response = ApiResponse.error(401, "未登录");

        assertEquals(401, response.code());
        assertEquals("未登录", response.message());
        assertNull(response.data());
    }
}
