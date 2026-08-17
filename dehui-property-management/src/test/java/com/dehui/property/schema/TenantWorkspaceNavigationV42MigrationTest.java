package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TenantWorkspaceNavigationV42MigrationTest {

    @Test
    void migrationCreatesOneTenantWorkspaceAndOnlyDisablesLegacyNavigation() throws Exception {
        String sql = new String(
                getClass().getResourceAsStream("/db/migration/V42__consolidate_tenant_contract_management.sql").readAllBytes(),
                StandardCharsets.UTF_8
        );

        assertTrue(sql.contains("'lease:tenant-workspace'"));
        assertTrue(sql.contains("'/tenant/workspace'"));
        assertTrue(sql.contains("'lease:tenant', 'lease:contract', 'lease:checkin'"));
        assertTrue(sql.contains("'lease:fee-rule-v2'"));
        assertTrue(sql.contains("'lease:billing-plan'"));
        assertTrue(sql.contains("SET rm.status = 'DISABLED'"));
        assertFalse(sql.contains("DELETE FROM"));
        assertFalse(sql.contains("DROP TABLE"));
        assertFalse(sql.contains("sys_role_permission"));
    }
}
