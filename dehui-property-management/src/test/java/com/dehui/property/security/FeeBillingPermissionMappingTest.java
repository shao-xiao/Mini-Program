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

class FeeBillingPermissionMappingTest {

    @Test
    void feeRuleRoutesUseExactHyphenatedPermissionCodes() throws Exception {
        assertAllowed("GET", "/api/fee-rules/search", "fee-rule:view");
        assertAllowed("POST", "/api/fee-rules", "fee-rule:create");
        assertAllowed("PUT", "/api/fee-rules/8", "fee-rule:update");
        assertDedicated("POST", "/api/fee-rules/8/activate", "fee-rule:activate", "fee-rule:update");
    }

    @Test
    void billingPlanRoutesUseDedicatedPermissions() throws Exception {
        assertAllowed("GET", "/api/billing-plans/search", "billing-plan:view");
        assertAllowed("POST", "/api/billing-plans", "billing-plan:create");
        assertAllowed("PUT", "/api/billing-plans/8", "billing-plan:update");
        assertAllowed("POST", "/api/billing-plans/8/activate", "billing-plan:update");
        assertDedicated("POST", "/api/billing-plans/8/terminate", "billing-plan:terminate", "billing-plan:update");
    }

    @Test
    void legacyFeeRuleCodesOnlyAuthorizeLegacyRoutes() throws Exception {
        assertAllowed("GET", "/api/feerules", "feerule:view");
        AccessResult result = authorize("GET", "/api/fee-rules/search", "feerule:view");
        assertFalse(result.allowed(), "legacy permission must not silently widen to the V2 workspace");
        assertEquals(403, result.status());
    }

    private static void assertDedicated(String method, String uri, String permission, String generic) throws Exception {
        assertAllowed(method, uri, permission);
        AccessResult result = authorize(method, uri, generic);
        assertFalse(result.allowed());
        assertEquals(403, result.status());
    }

    private static void assertAllowed(String method, String uri, String permission) throws Exception {
        AccessResult result = authorize(method, uri, permission);
        assertTrue(result.allowed(), permission + " must authorize " + method + " " + uri);
    }

    private static AccessResult authorize(String method, String uri, String permission) throws Exception {
        TokenService tokenService = mock(TokenService.class);
        when(tokenService.readToken("test-token")).thenReturn(Optional.of(new AuthPrincipal(
                8002L, "fee-billing-tester", "SYSTEM", null, List.of("STAFF"), List.of(permission)
        )));
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.addHeader("Authorization", "test-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        return new AccessResult(interceptor.preHandle(request, response, new Object()), response.getStatus());
    }

    private record AccessResult(boolean allowed, int status) {}
}
