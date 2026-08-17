package com.dehui.property.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthInterceptorPublicMobileVisitorTest {

    @Test
    void publicMeetingRoomsAndCapabilityScopedVisitorRoutesDoNotRequireLoginToken() throws Exception {
        TokenService tokenService = mock(TokenService.class);
        when(tokenService.readToken(null)).thenReturn(Optional.empty());
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, new ObjectMapper());

        for (MockHttpServletRequest request : List.of(
                new MockHttpServletRequest("GET", "/api/mobile/public/meeting-rooms"),
                new MockHttpServletRequest("GET", "/api/mobile/public/meeting-rooms/8/availability"),
                new MockHttpServletRequest("POST", "/api/mobile/public/visitors"),
                new MockHttpServletRequest("POST", "/api/mobile/public/visitors/history"),
                new MockHttpServletRequest(
                        "GET", "/api/mobile/public/visitors/PVR-ABCDEF0123456789ABCDEF0123456789/qr"
                )
        )) {
            assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        }

        MockHttpServletResponse visitorListResponse = new MockHttpServletResponse();
        assertEquals(false, interceptor.preHandle(
                new MockHttpServletRequest("GET", "/api/mobile/public/visitors"),
                visitorListResponse,
                new Object()
        ));
        assertEquals(401, visitorListResponse.getStatus());
    }

    @Test
    void guestMeetingSubmissionRequiresDedicatedCapabilityAndToken() throws Exception {
        TokenService tokenService = mock(TokenService.class);
        when(tokenService.readToken(null)).thenReturn(Optional.empty());
        when(tokenService.readToken("view-only")).thenReturn(Optional.of(new AuthPrincipal(
                22L, "mobile:22", "PUBLIC", null, List.of("MOBILE_USER"), List.of("meeting.public.view")
        )));
        when(tokenService.readToken("guest-book")).thenReturn(Optional.of(new AuthPrincipal(
                23L, "mobile:23", "PUBLIC", null, List.of("MOBILE_USER"),
                List.of("meeting.public.view", "meeting.guest.book")
        )));
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, new ObjectMapper());

        MockHttpServletResponse anonymousResponse = new MockHttpServletResponse();
        assertEquals(false, interceptor.preHandle(
                new MockHttpServletRequest("POST", "/api/mobile/public/meeting-bookings"),
                anonymousResponse,
                new Object()
        ));
        assertEquals(401, anonymousResponse.getStatus());

        MockHttpServletRequest denied = new MockHttpServletRequest(
                "POST", "/api/mobile/public/meeting-bookings"
        );
        denied.addHeader("Authorization", "view-only");
        MockHttpServletResponse deniedResponse = new MockHttpServletResponse();
        assertEquals(false, interceptor.preHandle(denied, deniedResponse, new Object()));
        assertEquals(403, deniedResponse.getStatus());

        MockHttpServletRequest allowed = new MockHttpServletRequest(
                "POST", "/api/mobile/public/meeting-bookings"
        );
        allowed.addHeader("Authorization", "guest-book");
        assertTrue(interceptor.preHandle(allowed, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void verifiedGuestCanReadMeetingSchemaButStaffCannotUseTenantVisitorEndpoint() throws Exception {
        TokenService tokenService = mock(TokenService.class);
        when(tokenService.readToken("guest-book")).thenReturn(Optional.of(new AuthPrincipal(
                23L, "mobile:23", "PUBLIC", null, List.of("MOBILE_USER"),
                List.of("meeting.public.view", "meeting.guest.book")
        )));
        when(tokenService.readToken("visitor-staff")).thenReturn(Optional.of(new AuthPrincipal(
                24L, "mobile:24", "STAFF", null, List.of("MOBILE_USER", "MOBILE_STAFF"),
                List.of("visitor.staff.view")
        )));
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, new ObjectMapper());

        MockHttpServletRequest schemaRequest = new MockHttpServletRequest(
                "GET", "/api/mobile/module-fields/meeting-bookings"
        );
        schemaRequest.addHeader("Authorization", "guest-book");
        assertTrue(interceptor.preHandle(schemaRequest, new MockHttpServletResponse(), new Object()));

        MockHttpServletRequest legacyVisitorRequest = new MockHttpServletRequest(
                "GET", "/api/mobile/visitors"
        );
        legacyVisitorRequest.addHeader("Authorization", "visitor-staff");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertEquals(false, interceptor.preHandle(legacyVisitorRequest, response, new Object()));
        assertEquals(403, response.getStatus());
    }

    @Test
    void mobileVisitorListAndCreateRequireLoginToken() throws Exception {
        TokenService tokenService = mock(TokenService.class);
        when(tokenService.readToken(null)).thenReturn(Optional.empty());
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, new ObjectMapper());

        MockHttpServletRequest listRequest = new MockHttpServletRequest("GET", "/api/mobile/visitors");
        MockHttpServletResponse listResponse = new MockHttpServletResponse();
        MockHttpServletRequest createRequest = new MockHttpServletRequest("POST", "/api/mobile/visitors");
        MockHttpServletResponse createResponse = new MockHttpServletResponse();

        assertEquals(false, interceptor.preHandle(listRequest, listResponse, new Object()));
        assertEquals(false, interceptor.preHandle(createRequest, createResponse, new Object()));
        assertEquals(401, listResponse.getStatus());
        assertEquals(401, createResponse.getStatus());
    }

    @Test
    void publicMobileUserReceivesForbiddenForAllTenantBusinessRoutes() throws Exception {
        TokenService tokenService = mock(TokenService.class);
        when(tokenService.readToken("public-token")).thenReturn(Optional.of(new AuthPrincipal(
                21L,
                "mobile:21",
                "PUBLIC",
                null,
                List.of("MOBILE_USER"),
                List.of("announcement.view", "investment.view", "investment.booking.create")
        )));
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, new ObjectMapper());

        for (String path : List.of(
                "/api/mobile/bills",
                "/api/mobile/contracts",
                "/api/mobile/workorders",
                "/api/mobile/meetings",
                "/api/mobile/visitors"
        )) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            request.addHeader("Authorization", "public-token");
            MockHttpServletResponse response = new MockHttpServletResponse();

            assertEquals(false, interceptor.preHandle(request, response, new Object()), path);
            assertEquals(403, response.getStatus(), path);
        }
    }

    @Test
    void wechatPhoneLoginRequiresExistingWechatSessionToken() throws Exception {
        TokenService tokenService = mock(TokenService.class);
        when(tokenService.readToken(null)).thenReturn(Optional.empty());
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, new ObjectMapper());
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(
                new MockHttpServletRequest("POST", "/api/mobile/auth/wechat-phone-login"),
                response,
                new Object()
        );

        assertEquals(false, allowed);
        assertEquals(401, response.getStatus());
    }

    @Test
    void backendUnbindImmediatelyRemovesTenantAccessFromOldToken() throws Exception {
        TokenService tokenService = mock(TokenService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AuthPrincipal staleTenant = new AuthPrincipal(
                21L,
                "mobile:21",
                "TENANT",
                6L,
                List.of("MOBILE_USER", "MOBILE_TENANT"),
                List.of("bill.view")
        );
        when(tokenService.readToken("tenant-token")).thenReturn(Optional.of(staleTenant));
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.argThat(sql -> sql != null && sql.contains("FROM mobile_user mobile")),
                org.mockito.ArgumentMatchers.any(Object[].class)
        )).thenReturn(List.of(Map.of(
                "mobileUserId", 21L,
                "declaredUserType", "PUBLIC"
        )));
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, new ObjectMapper(), jdbcTemplate);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/mobile/bills");
        request.addHeader("Authorization", "tenant-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertEquals(false, interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
    }

    @Test
    void historicalStaffTokenWithBlankUnverifiedPhoneCannotReachWorkbench() throws Exception {
        TokenService tokenService = mock(TokenService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AuthPrincipal staleStaff = new AuthPrincipal(
                53L,
                "mobile:53",
                "STAFF",
                null,
                List.of("MOBILE_USER", "MOBILE_STAFF"),
                List.of("staff.workbench.view", "inspection.staff.view")
        );
        when(tokenService.readToken("stale-staff-token")).thenReturn(Optional.of(staleStaff));
        Map<String, Object> identity = new HashMap<>();
        identity.put("mobileUserId", 53L);
        identity.put("phone", "");
        identity.put("phoneVerifiedAt", null);
        identity.put("declaredUserType", "INTERNAL");
        identity.put("currentBindType", "INTERNAL");
        identity.put("boundSysUserId", 1L);
        identity.put("staffPhone", "");
        identity.put("staffStatus", "ENABLED");
        identity.put("mobileEnabled", 1);
        identity.put("hasMobileWorkbenchAccess", 1);
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.argThat(sql -> sql != null && sql.contains("FROM mobile_user mobile")),
                org.mockito.ArgumentMatchers.any(Object[].class)
        )).thenReturn(List.of(identity));
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, new ObjectMapper(), jdbcTemplate);

        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/mobile/staff/workbench"
        );
        request.addHeader("Authorization", "stale-staff-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertEquals(false, interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
        verify(tokenService).revoke("stale-staff-token");
    }

    @Test
    void backendDisableImmediatelyInvalidatesOldMobileToken() throws Exception {
        TokenService tokenService = mock(TokenService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AuthPrincipal staleTenant = new AuthPrincipal(
                21L,
                "mobile:21",
                "TENANT",
                6L,
                List.of("MOBILE_USER", "MOBILE_TENANT"),
                List.of("bill.view")
        );
        when(tokenService.readToken("tenant-token")).thenReturn(Optional.of(staleTenant));
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.argThat(sql -> sql != null && sql.contains("FROM mobile_user mu")),
                org.mockito.ArgumentMatchers.any(Object[].class)
        )).thenReturn(List.of());
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, new ObjectMapper(), jdbcTemplate);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/mobile/bills");
        request.addHeader("Authorization", "tenant-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertEquals(false, interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void internalMobileUserCannotEnterFirstPhaseMobileWorkbench() throws Exception {
        TokenService tokenService = mock(TokenService.class);
        AuthPrincipal internalPrincipal = new AuthPrincipal(
                31L,
                "mobile:31",
                "INTERNAL",
                null,
                List.of("MOBILE_USER", "MOBILE_STAFF"),
                List.of("announcement.view", "investment.view", "investment.booking.create")
        );
        when(tokenService.readToken("internal-token")).thenReturn(Optional.of(internalPrincipal));
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, new ObjectMapper());

        for (String path : List.of("/api/mobile/checkins", "/api/mobile/workorders")) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            request.addHeader("Authorization", "internal-token");
            MockHttpServletResponse response = new MockHttpServletResponse();

            assertEquals(false, interceptor.preHandle(request, response, new Object()), path);
            assertEquals(403, response.getStatus(), path);
        }
    }

    @Test
    void backendApiWithoutRequiredPermissionReturnsForbiddenMessage() throws Exception {
        TokenService tokenService = mock(TokenService.class);
        AuthPrincipal principal = new AuthPrincipal(
                1001L,
                "engineer",
                "STAFF",
                null,
                List.of("ENGINEER"),
                List.of("workorder:view")
        );
        when(tokenService.readToken("token")).thenReturn(Optional.of(principal));
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, new ObjectMapper());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/bills");
        request.addHeader("Authorization", "token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertEquals(false, allowed);
        assertEquals(403, response.getStatus());
        assertTrue(!response.getContentAsString(StandardCharsets.UTF_8).isEmpty());
    }

    @Test
    void engineerWithOnlyViewAndCreateCanListAndCreateButCannotDeleteWorkOrders() throws Exception {
        TokenService tokenService = mock(TokenService.class);
        when(tokenService.readToken("tech-token")).thenReturn(Optional.of(new AuthPrincipal(
                1514L,
                "tech",
                "SYSTEM",
                null,
                List.of("ENGINEER"),
                List.of("workorder:view", "workorder:create")
        )));
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, new ObjectMapper());

        MockHttpServletRequest listRequest = new MockHttpServletRequest("GET", "/api/workorders");
        listRequest.addHeader("Authorization", "tech-token");
        assertTrue(interceptor.preHandle(listRequest, new MockHttpServletResponse(), new Object()));

        MockHttpServletRequest createRequest = new MockHttpServletRequest("POST", "/api/workorders");
        createRequest.addHeader("Authorization", "tech-token");
        assertTrue(interceptor.preHandle(createRequest, new MockHttpServletResponse(), new Object()));

        MockHttpServletRequest deleteRequest = new MockHttpServletRequest("DELETE", "/api/workorders/99");
        deleteRequest.addHeader("Authorization", "tech-token");
        MockHttpServletResponse deleteResponse = new MockHttpServletResponse();
        assertEquals(false, interceptor.preHandle(deleteRequest, deleteResponse, new Object()));
        assertEquals(403, deleteResponse.getStatus());
    }

    @Test
    void backendApiUsesCurrentDatabasePermissionsInsteadOfStaleTokenPermissions() throws Exception {
        TokenService tokenService = mock(TokenService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AuthPrincipal stalePrincipal = new AuthPrincipal(
                3001L,
                "stale-finance",
                "SYSTEM",
                null,
                List.of("FINANCE"),
                List.of("bill:view", "finance:bill:export")
        );
        when(tokenService.readToken("stale-token")).thenReturn(Optional.of(stalePrincipal));
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.argThat(sql -> sql != null && sql.contains("web_login_enabled = 1")),
                org.mockito.ArgumentMatchers.eq(Long.class),
                org.mockito.ArgumentMatchers.eq(3001L)
        )).thenReturn(List.of(3001L));
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.argThat(sql -> sql != null && sql.contains("SELECT r.code")),
                org.mockito.ArgumentMatchers.eq(String.class),
                org.mockito.ArgumentMatchers.eq(3001L)
        )).thenReturn(List.of("ENGINEER"));
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.argThat(sql -> sql != null && sql.contains("SELECT DISTINCT p.code")),
                org.mockito.ArgumentMatchers.eq(String.class),
                org.mockito.ArgumentMatchers.eq(3001L)
        )).thenReturn(List.of("workorder:view"));
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, new ObjectMapper(), jdbcTemplate);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/bills");
        request.addHeader("Authorization", "stale-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertEquals(false, interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
    }

    @Test
    void activeSystemUserWithoutRoleIsAuthenticatedButDeniedByPermission() throws Exception {
        TokenService tokenService = mock(TokenService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(tokenService.readToken("no-role-token")).thenReturn(Optional.of(new AuthPrincipal(
                3002L,
                "no-role",
                "SYSTEM",
                null,
                List.of(),
                List.of()
        )));
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.argThat(sql -> sql != null && sql.contains("web_login_enabled = 1")),
                org.mockito.ArgumentMatchers.eq(Long.class),
                org.mockito.ArgumentMatchers.eq(3002L)
        )).thenReturn(List.of(3002L));
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, new ObjectMapper(), jdbcTemplate);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/buildings");
        request.addHeader("Authorization", "no-role-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertEquals(false, interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
    }

    @Test
    void disablingWebLoginImmediatelyInvalidatesAnExistingSystemToken() throws Exception {
        TokenService tokenService = mock(TokenService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(tokenService.readToken("disabled-web-token")).thenReturn(Optional.of(new AuthPrincipal(
                3003L,
                "disabled-web",
                "SYSTEM",
                null,
                List.of("STAFF"),
                List.of("building:view")
        )));
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, new ObjectMapper(), jdbcTemplate);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/buildings");
        request.addHeader("Authorization", "disabled-web-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertEquals(false, interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void mobileUserAdminMutationRequiresMobileUpdatePermission() throws Exception {
        TokenService tokenService = mock(TokenService.class);
        when(tokenService.readToken("view-token")).thenReturn(Optional.of(new AuthPrincipal(
                1002L,
                "mobile-viewer",
                "STAFF",
                null,
                List.of("STAFF"),
                List.of("mobile:view")
        )));
        when(tokenService.readToken("update-token")).thenReturn(Optional.of(new AuthPrincipal(
                1003L,
                "mobile-operator",
                "STAFF",
                null,
                List.of("STAFF"),
                List.of("mobile:update")
        )));
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, new ObjectMapper());

        MockHttpServletRequest deniedRequest = new MockHttpServletRequest(
                "POST",
                "/api/mobile-users/1/bind-tenant"
        );
        deniedRequest.addHeader("Authorization", "view-token");
        MockHttpServletResponse deniedResponse = new MockHttpServletResponse();

        MockHttpServletRequest allowedRequest = new MockHttpServletRequest(
                "POST",
                "/api/mobile-users/1/bind-tenant"
        );
        allowedRequest.addHeader("Authorization", "update-token");
        MockHttpServletResponse allowedResponse = new MockHttpServletResponse();

        assertEquals(false, interceptor.preHandle(deniedRequest, deniedResponse, new Object()));
        assertEquals(403, deniedResponse.getStatus());
        assertTrue(interceptor.preHandle(allowedRequest, allowedResponse, new Object()));
    }

    @Test
    void legacyFinanceEndpointsDenyWithoutRequiredPermission() throws Exception {
        TokenService tokenService = mock(TokenService.class);
        when(tokenService.readToken("limited-token")).thenReturn(Optional.of(new AuthPrincipal(
                2001L,
                "limited-user",
                "STAFF",
                null,
                List.of("STAFF"),
                List.of("bill:add")
        )));
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, new ObjectMapper());

        for (String path : List.of("/api/payments", "/api/invoices", "/api/logs/operations", "/api/logs/logins")) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            request.addHeader("Authorization", "limited-token");
            MockHttpServletResponse response = new MockHttpServletResponse();

            assertEquals(false, interceptor.preHandle(request, response, new Object()), path + " should be forbidden without target permission");
            assertEquals(403, response.getStatus(), path + " should return 403");
        }
    }

    @Test
    void adminRoleDoesNotBypassEndpointPermissionsButSuperAdminDoes() throws Exception {
        TokenService tokenService = mock(TokenService.class);
        when(tokenService.readToken("admin-token")).thenReturn(Optional.of(new AuthPrincipal(
                2006L,
                "admin-user",
                "ADMIN",
                null,
                List.of("ADMIN"),
                List.of()
        )));
        when(tokenService.readToken("super-admin-token")).thenReturn(Optional.of(new AuthPrincipal(
                2007L,
                "super-admin-user",
                "SUPER_ADMIN",
                null,
                List.of("SUPER_ADMIN"),
                List.of()
        )));
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, new ObjectMapper());

        MockHttpServletRequest adminRequest = new MockHttpServletRequest("GET", "/api/payments");
        adminRequest.addHeader("Authorization", "admin-token");
        MockHttpServletResponse adminResponse = new MockHttpServletResponse();

        MockHttpServletRequest superAdminRequest = new MockHttpServletRequest("GET", "/api/payments");
        superAdminRequest.addHeader("Authorization", "super-admin-token");

        assertEquals(false, interceptor.preHandle(adminRequest, adminResponse, new Object()));
        assertEquals(403, adminResponse.getStatus());
        assertEquals(true, interceptor.preHandle(superAdminRequest, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void legacyFinanceAndLogEndpointsAllowWithRequiredPermission() throws Exception {
        TokenService tokenService = mock(TokenService.class);
        AuthPrincipal financePermission = new AuthPrincipal(
                2002L,
                "finance-user",
                "FINANCE",
                null,
                List.of("FINANCE"),
                List.of("bill:view")
        );
        AuthPrincipal systemPermission = new AuthPrincipal(
                2003L,
                "system-user",
                "PROPERTY_MANAGER",
                null,
                List.of("PROPERTY_MANAGER"),
                List.of("system:user:view")
        );
        when(tokenService.readToken("bill-token")).thenReturn(Optional.of(financePermission));
        when(tokenService.readToken("system-token")).thenReturn(Optional.of(systemPermission));
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, new ObjectMapper());

        MockHttpServletRequest paymentsRequest = new MockHttpServletRequest("GET", "/api/payments");
        paymentsRequest.addHeader("Authorization", "bill-token");
        assertEquals(true, interceptor.preHandle(paymentsRequest, new MockHttpServletResponse(), new Object()));

        MockHttpServletRequest invoicesRequest = new MockHttpServletRequest("GET", "/api/invoices");
        invoicesRequest.addHeader("Authorization", "bill-token");
        assertEquals(true, interceptor.preHandle(invoicesRequest, new MockHttpServletResponse(), new Object()));

        MockHttpServletRequest logOperationsRequest = new MockHttpServletRequest("GET", "/api/logs/operations");
        logOperationsRequest.addHeader("Authorization", "system-token");
        assertEquals(true, interceptor.preHandle(logOperationsRequest, new MockHttpServletResponse(), new Object()));

        MockHttpServletRequest loginsRequest = new MockHttpServletRequest("GET", "/api/logs/logins");
        loginsRequest.addHeader("Authorization", "system-token");
        assertEquals(true, interceptor.preHandle(loginsRequest, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void invoiceUploadUsesFinanceInvoiceUploadPermissionInsteadOfBillAdd() throws Exception {
        TokenService tokenService = mock(TokenService.class);
        when(tokenService.readToken("bill-add-token")).thenReturn(Optional.of(new AuthPrincipal(
                2004L,
                "bill-add-only",
                "FINANCE",
                null,
                List.of("FINANCE"),
                List.of("bill:add")
        )));
        when(tokenService.readToken("invoice-upload-token")).thenReturn(Optional.of(new AuthPrincipal(
                2005L,
                "invoice-upload-user",
                "FINANCE",
                null,
                List.of("FINANCE"),
                List.of("finance:invoice:upload")
        )));
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, new ObjectMapper());

        MockHttpServletRequest deniedRequest = new MockHttpServletRequest("POST", "/api/bills/1/invoice");
        deniedRequest.addHeader("Authorization", "bill-add-token");
        MockHttpServletResponse deniedResponse = new MockHttpServletResponse();
        assertEquals(false, interceptor.preHandle(deniedRequest, deniedResponse, new Object()));
        assertEquals(403, deniedResponse.getStatus());

        MockHttpServletRequest allowedRequest = new MockHttpServletRequest("POST", "/api/bills/1/invoice");
        allowedRequest.addHeader("Authorization", "invoice-upload-token");
        assertEquals(true, interceptor.preHandle(allowedRequest, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void parkingBillPaymentAndVoidRequireTheirDedicatedPermissions() throws Exception {
        TokenService tokenService = mock(TokenService.class);
        when(tokenService.readToken("parking-view-token")).thenReturn(Optional.of(new AuthPrincipal(
                3001L,
                "parking-view-user",
                "SYSTEM",
                null,
                List.of("STAFF"),
                List.of("parking-bill:view")
        )));
        when(tokenService.readToken("parking-pay-token")).thenReturn(Optional.of(new AuthPrincipal(
                3002L,
                "parking-pay-user",
                "SYSTEM",
                null,
                List.of("CASHIER"),
                List.of("parking-bill:pay")
        )));
        when(tokenService.readToken("parking-update-token")).thenReturn(Optional.of(new AuthPrincipal(
                3003L,
                "parking-update-user",
                "SYSTEM",
                null,
                List.of("PROPERTY_MANAGER"),
                List.of("parking-bill:update")
        )));
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, new ObjectMapper());

        MockHttpServletRequest deniedPay = new MockHttpServletRequest("POST", "/api/parking/bills/9/pay");
        deniedPay.addHeader("Authorization", "parking-view-token");
        MockHttpServletResponse deniedPayResponse = new MockHttpServletResponse();
        assertEquals(false, interceptor.preHandle(deniedPay, deniedPayResponse, new Object()));
        assertEquals(403, deniedPayResponse.getStatus());

        MockHttpServletRequest allowedPay = new MockHttpServletRequest("POST", "/api/parking/bills/9/pay");
        allowedPay.addHeader("Authorization", "parking-pay-token");
        assertEquals(true, interceptor.preHandle(allowedPay, new MockHttpServletResponse(), new Object()));

        MockHttpServletRequest deniedVoid = new MockHttpServletRequest("POST", "/api/parking/bills/9/void");
        deniedVoid.addHeader("Authorization", "parking-view-token");
        MockHttpServletResponse deniedVoidResponse = new MockHttpServletResponse();
        assertEquals(false, interceptor.preHandle(deniedVoid, deniedVoidResponse, new Object()));
        assertEquals(403, deniedVoidResponse.getStatus());

        MockHttpServletRequest allowedVoid = new MockHttpServletRequest("POST", "/api/parking/bills/9/void");
        allowedVoid.addHeader("Authorization", "parking-update-token");
        assertEquals(true, interceptor.preHandle(allowedVoid, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void parkingAssignmentMonthlyFeeUsesExistingParkingUpdatePermission() throws Exception {
        TokenService tokenService = mock(TokenService.class);
        when(tokenService.readToken("parking-read-token")).thenReturn(Optional.of(new AuthPrincipal(
                3011L,
                "parking-read-user",
                "SYSTEM",
                null,
                List.of("STAFF"),
                List.of("parking:view")
        )));
        when(tokenService.readToken("parking-write-token")).thenReturn(Optional.of(new AuthPrincipal(
                3012L,
                "parking-write-user",
                "SYSTEM",
                null,
                List.of("PROPERTY_MANAGER"),
                List.of("parking:update")
        )));
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, new ObjectMapper());

        MockHttpServletRequest denied = new MockHttpServletRequest(
                "PATCH",
                "/api/parking/assignments/9/monthly-fee"
        );
        denied.addHeader("Authorization", "parking-read-token");
        MockHttpServletResponse deniedResponse = new MockHttpServletResponse();
        assertEquals(false, interceptor.preHandle(denied, deniedResponse, new Object()));
        assertEquals(403, deniedResponse.getStatus());

        MockHttpServletRequest allowed = new MockHttpServletRequest(
                "PATCH",
                "/api/parking/assignments/9/monthly-fee"
        );
        allowed.addHeader("Authorization", "parking-write-token");
        assertEquals(true, interceptor.preHandle(allowed, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void customFieldRoutesUseWorkspaceRegistryAndDedicatedManagePermission() throws Exception {
        TokenService tokenService = mock(TokenService.class);
        when(tokenService.readToken("room-view")).thenReturn(Optional.of(new AuthPrincipal(
                4001L, "room-reader", "SYSTEM", null, List.of("STAFF"), List.of("room:view")
        )));
        when(tokenService.readToken("field-manager")).thenReturn(Optional.of(new AuthPrincipal(
                4002L, "field-manager", "SYSTEM", null, List.of("PROPERTY_MANAGER"),
                List.of("custom-field:manage", "room:update")
        )));
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, new ObjectMapper());

        MockHttpServletRequest read = new MockHttpServletRequest("GET", "/api/workspaces/rooms/custom-fields");
        read.addHeader("Authorization", "room-view");
        assertTrue(interceptor.preHandle(read, new MockHttpServletResponse(), new Object()));

        MockHttpServletRequest deniedCreate = new MockHttpServletRequest("POST", "/api/workspaces/rooms/custom-fields");
        deniedCreate.addHeader("Authorization", "room-view");
        MockHttpServletResponse deniedCreateResponse = new MockHttpServletResponse();
        assertEquals(false, interceptor.preHandle(deniedCreate, deniedCreateResponse, new Object()));
        assertEquals(403, deniedCreateResponse.getStatus());

        MockHttpServletRequest allowedCreate = new MockHttpServletRequest("POST", "/api/workspaces/rooms/custom-fields");
        allowedCreate.addHeader("Authorization", "field-manager");
        assertTrue(interceptor.preHandle(allowedCreate, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void customFieldValueMutationUsesExistingWorkspaceUpdatePermission() throws Exception {
        TokenService tokenService = mock(TokenService.class);
        when(tokenService.readToken("room-view")).thenReturn(Optional.of(new AuthPrincipal(
                4011L, "room-reader", "SYSTEM", null, List.of("STAFF"), List.of("room:view")
        )));
        when(tokenService.readToken("room-update")).thenReturn(Optional.of(new AuthPrincipal(
                4012L, "room-editor", "SYSTEM", null, List.of("PROPERTY_MANAGER"), List.of("room:update")
        )));
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, new ObjectMapper());

        MockHttpServletRequest denied = new MockHttpServletRequest(
                "PUT", "/api/workspaces/rooms/records/8/custom-fields"
        );
        denied.addHeader("Authorization", "room-view");
        MockHttpServletResponse deniedResponse = new MockHttpServletResponse();
        assertEquals(false, interceptor.preHandle(denied, deniedResponse, new Object()));
        assertEquals(403, deniedResponse.getStatus());

        MockHttpServletRequest allowed = new MockHttpServletRequest(
                "PUT", "/api/custom-fields/rooms/records/8"
        );
        allowed.addHeader("Authorization", "room-update");
        assertTrue(interceptor.preHandle(allowed, new MockHttpServletResponse(), new Object()));
    }
}
