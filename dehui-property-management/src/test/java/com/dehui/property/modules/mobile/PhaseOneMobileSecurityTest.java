package com.dehui.property.modules.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dehui.property.modules.customfield.application.CustomFieldApplicationService;
import com.dehui.property.modules.customfield.application.ModuleFieldRegistry;
import com.dehui.property.modules.customfield.application.ModuleFieldSchemaService;
import com.dehui.property.modules.mobile.service.MobileCapabilityMapper;
import com.dehui.property.modules.mobile.service.MobileIdentityService;
import com.dehui.property.security.AuthPrincipal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PhaseOneMobileSecurityTest {

    @Test
    void readPermissionsMapToStaffViewCapabilities() {
        List<String> capabilities = MobileCapabilityMapper.fromPermissionCodes(List.of(
                "staff-mobile:access",
                "staff-mobile:workorder:view",
                "staff-mobile:inspection:view",
                "staff-mobile:energy:view",
                "staff-mobile:meeting:view",
                "staff-mobile:visitor:view"
        ));
        for (String capability : List.of(
                "workorder.staff.view",
                "inspection.staff.view",
                "energy.staff.view",
                "meeting.staff.view",
                "visitor.staff.view"
        )) {
            assertTrue(capabilities.contains(capability), capability);
        }
    }

    @Test
    void writePermissionsMapToExactStaffOperations() {
        List<String> capabilities = MobileCapabilityMapper.fromPermissionCodes(List.of(
                "staff-mobile:access",
                "staff-mobile:workorder:write",
                "staff-mobile:inspection:write",
                "staff-mobile:meeting:write",
                "staff-mobile:visitor:write"
        ));
        for (String capability : List.of(
                "workorder.staff.start",
                "workorder.staff.complete",
                "inspection.staff.create",
                "inspection.staff.update",
                "meeting.staff.manage",
                "visitor.staff.verify"
        )) {
            assertTrue(capabilities.contains(capability), capability);
        }
    }

    @Test
    void roleNamesNeverCreateMobileBusinessCapabilities() {
        List<String> capabilities = MobileCapabilityMapper.fromPermissionCodes(List.of(
                "STAFF",
                "ADMIN",
                "PROPERTY_MANAGER"
        ));
        assertEquals(List.of(), capabilities);
        assertFalse(MobileCapabilityMapper.hasWorkbenchAccess(List.of("STAFF")));
    }

    @Test
    void phoneNormalizationAcceptsOfficialCountryPrefixes() {
        assertEquals("13800001234", MobileIdentityService.normalizePhone("+86 138-0000-1234"));
        assertEquals("13800001234", MobileIdentityService.normalizePhone("0086(138)00001234"));
        assertEquals("", MobileIdentityService.normalizePhone(""));
        assertEquals("", MobileIdentityService.normalizePhone("1380000123"));
        assertEquals("", MobileIdentityService.normalizePhone("913800001234"));
        assertEquals("", MobileIdentityService.normalizePhone("9913800001234"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void mobileSchemaRemovesRelationshipFieldsButKeepsBusinessFields() {
        ModuleFieldRegistry registry = mock(ModuleFieldRegistry.class);
        CustomFieldApplicationService customFields = mock(CustomFieldApplicationService.class);
        AuthPrincipal principal = new AuthPrincipal(
                7L, "mobile:7", "TENANT", 8L, List.of(), List.of()
        );
        Map<String, Object> surface = Map.of(
                "miniProgramForm", Map.of("visible", true, "editable", true, "order", 1)
        );
        when(registry.canonicalModuleKey("visitor")).thenReturn("visitor");
        when(registry.systemFields("visitor")).thenReturn(List.of(
                Map.of(
                        "fieldKey", "tenantId",
                        "apiField", "tenantId",
                        "storageField", "tenant_id",
                        "surfaces", surface
                ),
                Map.of(
                        "fieldKey", "visitorPhone",
                        "apiField", "visitorPhone",
                        "storageField", "visitor_phone",
                        "surfaces", surface
                )
        ));
        when(customFields.definitions(eq("visitor"), eq(false), any())).thenReturn(List.of());

        Map<String, Object> schema =
                new ModuleFieldSchemaService(registry, customFields)
                        .mobileSchema("visitor", "MINIPROGRAM_FORM", principal);
        List<Map<String, Object>> fields = (List<Map<String, Object>>) schema.get("fields");

        assertEquals(List.of("visitorPhone"),
                fields.stream().map(field -> String.valueOf(field.get("fieldKey"))).toList());
    }

    @Test
    void v47AddsIdentitySelectionMobileEnablementAndVisitorApplicant() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V47__mobile_identity_rbac_and_data_scope.sql"
        ));
        assertTrue(sql.contains("mobile_enabled"));
        assertTrue(sql.contains("current_bind_id"));
        assertTrue(sql.contains("mobile_identity_candidate"));
        assertTrue(sql.contains("token_hash"));
        assertTrue(sql.contains("applicant_mobile_user_id"));
    }

    @Test
    void workorderEmployeeScopeUsesSubmitterMobileUserId() throws Exception {
        String source = source(
                "src/main/java/com/dehui/property/modules/workorder/controller/WorkOrderController.java"
        );
        assertTrue(source.contains("(? = 1 OR w.submitter_mobile_user_id = ?)"));
        assertTrue(source.contains("(? = 1 OR submitter_mobile_user_id = ?)"));
        assertTrue(source.contains("principal.userId()"));
    }

    @Test
    void visitorEmployeeScopeUsesApplicantMobileUserId() throws Exception {
        String source = source(
                "src/main/java/com/dehui/property/modules/visitor/controller/VisitorController.java"
        );
        assertTrue(source.contains("applicant_mobile_user_id"));
        assertTrue(source.contains("(? = 1 OR applicant_mobile_user_id = ?)"));
        assertTrue(source.contains("principal.userId()"));
    }

    @Test
    void staffEndpointsRequireCapabilitiesAndRealStaffBinding() throws Exception {
        String workorders = source(
                "src/main/java/com/dehui/property/modules/workorder/controller/WorkOrderController.java"
        );
        String visitors = source(
                "src/main/java/com/dehui/property/modules/visitor/controller/VisitorController.java"
        );
        assertTrue(workorders.contains("requireCapability(principal, \"workorder.staff.start\")"));
        assertTrue(workorders.contains("requireCapability(principal, \"workorder.staff.complete\")"));
        assertTrue(workorders.contains("requireStaffUserId(jdbcTemplate, principal)"));
        assertTrue(visitors.contains("requireCapability(principal, \"visitor.staff.verify\")"));
    }

    @Test
    void mobileMeContractExcludesDatabaseRelationshipIds() throws Exception {
        String source = source(
                "src/main/java/com/dehui/property/modules/mobile/controller/MobileApiSupport.java"
        );
        String meMethod = source.substring(
                source.indexOf("public static Map<String, Object> me("),
                source.indexOf("public static Long tenantId(")
        );
        for (String forbidden : List.of(
                "me.put(\"tenantId\"",
                "me.put(\"tenantContactId\"",
                "me.put(\"sysUserId\"",
                "me.put(\"mobileUserId\"",
                "me.put(\"phone\""
        )) {
            assertFalse(meMethod.contains(forbidden), forbidden);
        }
    }

    @Test
    void candidateTokensAreHashedExpiringAndSingleUse() throws Exception {
        String source = source(
                "src/main/java/com/dehui/property/modules/mobile/service/MobileIdentityService.java"
        );
        assertTrue(source.contains("token_hash = ?"));
        assertTrue(source.contains("candidate_status = 'PENDING'"));
        assertTrue(source.contains("candidate_status = 'USED'"));
        assertTrue(source.contains("expires_at > NOW()"));
        assertTrue(source.contains("MessageDigest.getInstance(\"SHA-256\")"));
    }

    @Test
    void identityCandidateQueriesKeepTenantRowsIndependentOfStaffRbac() throws Exception {
        String source = source(
                "src/main/java/com/dehui/property/modules/mobile/service/MobileIdentityService.java"
        );
        int cursor = 0;
        int checkedQueries = 0;
        while ((cursor = source.indexOf("LEFT JOIN sys_user staff", cursor)) >= 0) {
            int where = source.indexOf("WHERE bind_record.mobile_user_id", cursor);
            assertTrue(where > cursor);
            assertFalse(
                    source.substring(cursor, where).contains("AND EXISTS"),
                    "STAFF RBAC inside LEFT JOIN can eliminate valid TENANT identities in MySQL"
            );
            cursor = where;
            checkedQueries++;
        }
        assertEquals(2, checkedQueries);
        assertTrue(source.contains(
                "validBindIds.size() > 1 ? issueCandidates(resolvedMobileUserId) : List.of()"
        ));
    }

    private String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
