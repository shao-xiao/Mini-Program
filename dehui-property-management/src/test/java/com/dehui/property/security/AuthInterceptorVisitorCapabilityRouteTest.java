package com.dehui.property.security;

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

class AuthInterceptorVisitorCapabilityRouteTest {

    @Test
    void onlyCapabilityScopedPublicVisitorRoutesBypassLogin() throws Exception {
        TokenService tokenService = mock(TokenService.class);
        when(tokenService.readToken(null)).thenReturn(Optional.empty());
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, new ObjectMapper());

        for (MockHttpServletRequest request : List.of(
                new MockHttpServletRequest("POST", "/api/mobile/public/visitors"),
                new MockHttpServletRequest("POST", "/api/mobile/public/visitors/history"),
                new MockHttpServletRequest(
                        "GET",
                        "/api/mobile/public/visitors/PVR-ABCDEF0123456789ABCDEF0123456789/qr"
                )
        )) {
            assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        }

        assertFalse(interceptor.preHandle(
                new MockHttpServletRequest("GET", "/api/mobile/public/visitors"),
                new MockHttpServletResponse(),
                new Object()
        ));
    }
}
