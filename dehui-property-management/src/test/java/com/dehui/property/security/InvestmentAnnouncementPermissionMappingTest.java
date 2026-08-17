package com.dehui.property.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InvestmentAnnouncementPermissionMappingTest {

    @Test
    void announcementPublishAndDisableUseUpdatePermission() throws Exception {
        assertAllowed("POST", "/api/announcements/8/publish", "announcement:update");
        assertAllowed("POST", "/api/announcements/8/disable", "announcement:update");

        AccessResult publishWithCreate = authorize("POST", "/api/announcements/8/publish", "announcement:create");
        assertFalse(publishWithCreate.allowed());
        assertEquals(403, publishWithCreate.status());
    }

    @Test
    void announcementCreateStillUsesCreatePermission() throws Exception {
        assertAllowed("POST", "/api/announcements", "announcement:create");
    }

    private static void assertAllowed(String method, String uri, String permission) throws Exception {
        AccessResult result = authorize(method, uri, permission);
        assertTrue(result.allowed(), permission + " must authorize " + method + " " + uri);
    }

    private static AccessResult authorize(String method, String uri, String permission) throws Exception {
        TokenService tokenService = mock(TokenService.class);
        when(tokenService.readToken("test-token")).thenReturn(Optional.of(new AuthPrincipal(
                8003L, "investment-announcement-tester", "SYSTEM", null, List.of("STAFF"), List.of(permission)
        )));
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.addHeader("Authorization", "test-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        return new AccessResult(interceptor.preHandle(request, response, new Object()), response.getStatus());
    }

    private record AccessResult(boolean allowed, int status) {}
}
