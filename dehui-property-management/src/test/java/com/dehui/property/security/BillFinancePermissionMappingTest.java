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

class BillFinancePermissionMappingTest {

    @Test
    void financeLedgerReadsUseBillViewPermission() throws Exception {
        assertAllowed("GET", "/api/bills/tenant-directory", "bill:view");
        assertAllowed("GET", "/api/bills/contracts", "bill:view");
        assertAllowed("GET", "/api/bills/payments", "bill:view");
        assertAllowed("GET", "/api/bills/invoices", "bill:view");

        AccessResult result = authorize("GET", "/api/bills/payments", "bill:pay");
        assertFalse(result.allowed());
        assertEquals(403, result.status());
    }

    @Test
    void financeDashboardRequiresItsDedicatedViewPermission() throws Exception {
        assertAllowed("GET", "/api/finance/dashboard", "finance:dashboard:view");

        AccessResult result = authorize("GET", "/api/finance/dashboard", "finance:bill:view");
        assertFalse(result.allowed());
        assertEquals(403, result.status());
    }

    @Test
    void paymentMutationStillRequiresBillPayPermission() throws Exception {
        assertAllowed("POST", "/api/bills/9/pay", "bill:pay");

        AccessResult result = authorize("POST", "/api/bills/9/pay", "bill:view");
        assertFalse(result.allowed());
        assertEquals(403, result.status());
    }

    @Test
    void tenantArchiveBillsRequireTenantAndFinanceReadPermissions() throws Exception {
        AccessResult allowed = authorize(
                "GET",
                "/api/tenants/12/archive/bills",
                List.of("tenant:view", "bill:view")
        );
        assertTrue(allowed.allowed());

        assertFalse(authorize("GET", "/api/tenants/12/archive/bills", "tenant:view").allowed());
        assertFalse(authorize("GET", "/api/tenants/12/archive/bills", "bill:view").allowed());
    }

    private static void assertAllowed(String method, String uri, String permission) throws Exception {
        AccessResult result = authorize(method, uri, permission);
        assertTrue(result.allowed(), permission + " must authorize " + method + " " + uri);
    }

    private static AccessResult authorize(String method, String uri, String permission) throws Exception {
        return authorize(method, uri, List.of(permission));
    }

    private static AccessResult authorize(String method, String uri, List<String> permissions) throws Exception {
        TokenService tokenService = mock(TokenService.class);
        when(tokenService.readToken("test-token")).thenReturn(Optional.of(new AuthPrincipal(
                9001L, "finance-tester", "SYSTEM", null, List.of("FINANCE"), permissions
        )));
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.addHeader("Authorization", "test-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        return new AccessResult(interceptor.preHandle(request, response, new Object()), response.getStatus());
    }

    private record AccessResult(boolean allowed, int status) {}
}
