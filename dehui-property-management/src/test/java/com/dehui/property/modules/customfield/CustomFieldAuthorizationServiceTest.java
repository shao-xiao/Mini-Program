package com.dehui.property.modules.customfield;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.customfield.application.CustomFieldAuthorizationService;
import com.dehui.property.modules.customfield.application.CustomFieldWorkspaceRegistry;
import com.dehui.property.security.AuthPrincipal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CustomFieldAuthorizationServiceTest {

    private final CustomFieldWorkspaceRegistry registry = new CustomFieldWorkspaceRegistry();
    private final CustomFieldAuthorizationService authorization = new CustomFieldAuthorizationService();

    @Test
    void definitionManagementRequiresGlobalAndWorkspacePermission() {
        var workspace = registry.require("rooms");
        assertDoesNotThrow(() -> authorization.requireManage(principal(
                "custom-field:manage", "room:update"), workspace));

        BusinessException missingGlobal = assertThrows(BusinessException.class,
                () -> authorization.requireManage(principal("room:update"), workspace));
        assertEquals(403, missingGlobal.getCode());

        BusinessException missingWorkspace = assertThrows(BusinessException.class,
                () -> authorization.requireManage(principal("custom-field:manage"), workspace));
        assertEquals(403, missingWorkspace.getCode());
    }

    @Test
    void readingAndValueEditingUseExistingWorkspacePermissions() {
        var workspace = registry.require("equipment");
        assertDoesNotThrow(() -> authorization.requireView(principal("asset:view"), workspace));
        assertDoesNotThrow(() -> authorization.requireValueUpdate(principal("asset:update"), workspace));
        assertThrows(BusinessException.class,
                () -> authorization.requireValueUpdate(principal("asset:view"), workspace));
    }

    @Test
    void onlySuperAdminRoleReceivesRoleBasedFullAccess() {
        var workspace = registry.require("bills");
        AuthPrincipal superAdmin = new AuthPrincipal(1L, "root", "SYSTEM", null, List.of("SUPER_ADMIN"), List.of());
        assertDoesNotThrow(() -> authorization.requireManage(superAdmin, workspace));

        AuthPrincipal admin = new AuthPrincipal(2L, "admin", "SYSTEM", null, List.of("ADMIN"), List.of());
        assertThrows(BusinessException.class, () -> authorization.requireManage(admin, workspace));
    }

    @Test
    void mobileCapabilitiesCanReadTheirMatchingWorkspaceSchemasOnly() {
        AuthPrincipal tenant = new AuthPrincipal(
                9L,
                "mobile:9",
                "TENANT",
                5L,
                List.of("MOBILE_TENANT"),
                List.of("visitor.create", "meeting.book", "parking.mine.view")
        );
        assertDoesNotThrow(() -> authorization.requireView(tenant, registry.require("visitors")));
        assertDoesNotThrow(() -> authorization.requireView(tenant, registry.require("meeting-bookings")));
        assertDoesNotThrow(() -> authorization.requireView(tenant, registry.require("parking-assignments")));
        assertThrows(BusinessException.class,
                () -> authorization.requireView(tenant, registry.require("energy-records")));
    }

    @Test
    void verifiedPublicGuestCanReadMeetingBookingSchemaOnlyWithGuestCapability() {
        AuthPrincipal verifiedGuest = new AuthPrincipal(
                21L,
                "mobile:21",
                "PUBLIC",
                null,
                List.of("MOBILE_USER"),
                List.of("meeting.public.view", "meeting.guest.book")
        );
        AuthPrincipal unverifiedGuest = new AuthPrincipal(
                22L,
                "mobile:22",
                "PUBLIC",
                null,
                List.of("MOBILE_USER"),
                List.of("meeting.public.view")
        );

        assertDoesNotThrow(() -> authorization.requireView(
                verifiedGuest,
                registry.require("meeting-bookings")
        ));
        assertThrows(BusinessException.class, () -> authorization.requireView(
                unverifiedGuest,
                registry.require("meeting-bookings")
        ));
    }

    private static AuthPrincipal principal(String... permissions) {
        return new AuthPrincipal(9L, "tester", "SYSTEM", null, List.of(), List.of(permissions));
    }
}
