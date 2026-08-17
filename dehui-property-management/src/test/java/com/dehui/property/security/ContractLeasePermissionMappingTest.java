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

class ContractLeasePermissionMappingTest {

    @Test
    void contractLifecycleRoutesRequireDedicatedActions() throws Exception {
        assertDedicatedAction("POST", "/api/contracts/42/approve", "contract:approve", "contract:create");
        assertDedicatedAction("POST", "/api/contracts/42/activate", "contract:activate", "contract:create");
        assertDedicatedAction("POST", "/api/contracts/42/terminate", "contract:terminate", "contract:create");
        assertDedicatedAction("POST", "/api/contracts/42/cancel", "contract:cancel", "contract:create");
    }

    @Test
    void leaseLifecycleRoutesRequireDedicatedActions() throws Exception {
        assertDedicatedAction("POST", "/api/leases/42/check-in", "lease:checkin", "lease:create");
        assertDedicatedAction("POST", "/api/leases/42/check-out", "lease:checkout", "lease:create");
        assertDedicatedAction("POST", "/api/leases/42/renew", "lease:renew", "lease:create");
        assertDedicatedAction("POST", "/api/leases/42/terminate", "lease:terminate", "lease:create");
    }

    @Test
    void contractAndLeaseCrudKeepTheirOwnPermissions() throws Exception {
        assertAllowed("GET", "/api/contracts/search", "contract:view");
        assertAllowed("POST", "/api/contracts", "contract:create");
        assertAllowed("PUT", "/api/contracts/42", "contract:update");
        assertAllowed("GET", "/api/leases/search", "lease:view");
        assertAllowed("POST", "/api/leases", "lease:create");
        AccessResult denied = authorize("POST", "/api/leases", "lease:view");
        assertFalse(denied.allowed());
        assertEquals(403, denied.status());
    }

    private static void assertDedicatedAction(
            String method,
            String uri,
            String dedicatedPermission,
            String genericPermission
    ) throws Exception {
        assertAllowed(method, uri, dedicatedPermission);
        AccessResult generic = authorize(method, uri, genericPermission);
        assertFalse(generic.allowed(), genericPermission + " must not authorize " + method + " " + uri);
        assertEquals(403, generic.status());
    }

    private static void assertAllowed(String method, String uri, String permission) throws Exception {
        AccessResult result = authorize(method, uri, permission);
        assertTrue(result.allowed(), permission + " must authorize " + method + " " + uri);
    }

    private static AccessResult authorize(String method, String uri, String permission) throws Exception {
        TokenService tokenService = mock(TokenService.class);
        when(tokenService.readToken("test-token")).thenReturn(Optional.of(new AuthPrincipal(
                7001L,
                "contract-lease-tester",
                "SYSTEM",
                null,
                List.of("STAFF"),
                List.of(permission)
        )));
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.addHeader("Authorization", "test-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        return new AccessResult(interceptor.preHandle(request, response, new Object()), response.getStatus());
    }

    private record AccessResult(boolean allowed, int status) {
    }
}
