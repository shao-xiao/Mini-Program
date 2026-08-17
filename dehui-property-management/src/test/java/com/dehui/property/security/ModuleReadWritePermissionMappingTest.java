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

class ModuleReadWritePermissionMappingTest {

    @Test
    void readPermissionAllowsQueriesButRejectsWrites() throws Exception {
        assertAllowed("GET", "/api/equipment", "asset:view");
        assertForbidden("POST", "/api/equipment", "asset:view");

        assertAllowed("GET", "/api/bills", "bill:view");
        assertForbidden("PUT", "/api/bills/9", "bill:view");

        assertAllowed("GET", "/api/inspections", "inspection:view");
        assertForbidden("DELETE", "/api/inspections/9", "inspection:view");
    }

    @Test
    void concreteWritePermissionsAllowTheirMappedMutations() throws Exception {
        assertAllowed("POST", "/api/equipment", "asset:create");
        assertAllowed("PUT", "/api/bills/9", "bill:update");
        assertAllowed("DELETE", "/api/bills/9", "bill:void");
        assertAllowed("DELETE", "/api/inspections/9", "inspection:delete");
    }

    @Test
    void billRecordDeletionUsesDedicatedDeletePermissionWithoutChangingVoidCompatibility() throws Exception {
        assertAllowed("DELETE", "/api/bills/9/record", "bill:delete");
        assertForbidden("DELETE", "/api/bills/9/record", "bill:void");
        assertAllowed("POST", "/api/bills/9/record/check", "bill:delete");
        assertForbidden("POST", "/api/bills/9/record/check", "bill:void");
        assertAllowed("POST", "/api/bills/9/void", "bill:void");
    }

    @Test
    void missingModulePermissionRejectsDirectApiAccess() throws Exception {
        assertForbidden("GET", "/api/equipment", "bill:view");
        assertForbidden("POST", "/api/bills", "asset:create");
    }

    private static void assertAllowed(String method, String uri, String permission) throws Exception {
        AccessResult result = authorize(method, uri, permission);
        assertTrue(result.allowed(), permission + " must authorize " + method + " " + uri);
    }

    private static void assertForbidden(String method, String uri, String permission) throws Exception {
        AccessResult result = authorize(method, uri, permission);
        assertFalse(result.allowed(), permission + " must not authorize " + method + " " + uri);
        assertEquals(403, result.status());
    }

    private static AccessResult authorize(String method, String uri, String permission) throws Exception {
        TokenService tokenService = mock(TokenService.class);
        when(tokenService.readToken("test-token")).thenReturn(Optional.of(new AuthPrincipal(
                9100L, "module-access-tester", "SYSTEM", null, List.of("STAFF"), List.of(permission)
        )));
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.addHeader("Authorization", "test-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        return new AccessResult(interceptor.preHandle(request, response, new Object()), response.getStatus());
    }

    private record AccessResult(boolean allowed, int status) {}
}
