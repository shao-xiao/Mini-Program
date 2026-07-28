package com.dehui.property.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthInterceptorVisitorReviewTest {

    @Test
    void visitorReviewAndQrBindingRequireVisitorUpdatePermission() throws Exception {
        TokenService tokenService = mock(TokenService.class);
        when(tokenService.readToken("visitor-reader")).thenReturn(Optional.of(new AuthPrincipal(
                71L, "visitor-reader", "SYSTEM", null, List.of("STAFF"), List.of("visitor:view")
        )));
        when(tokenService.readToken("visitor-reviewer")).thenReturn(Optional.of(new AuthPrincipal(
                72L, "visitor-reviewer", "SYSTEM", null, List.of("PROPERTY_MANAGER"),
                List.of("visitor:view", "visitor:update")
        )));
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, new ObjectMapper());

        for (RequestCase requestCase : List.of(
                new RequestCase("PATCH", "/api/visitors/9/approve"),
                new RequestCase("PATCH", "/api/visitors/9/reject"),
                new RequestCase("PUT", "/api/visitors/9/qr-code")
        )) {
            MockHttpServletRequest denied = requestCase.request("visitor-reader");
            MockHttpServletResponse deniedResponse = new MockHttpServletResponse();
            assertEquals(false, interceptor.preHandle(denied, deniedResponse, new Object()));
            assertEquals(403, deniedResponse.getStatus());

            assertTrue(interceptor.preHandle(
                    requestCase.request("visitor-reviewer"),
                    new MockHttpServletResponse(),
                    new Object()
            ));
        }
    }

    private record RequestCase(String method, String path) {
        MockHttpServletRequest request(String token) {
            MockHttpServletRequest request = new MockHttpServletRequest(method, path);
            request.addHeader("Authorization", token);
            return request;
        }
    }
}
