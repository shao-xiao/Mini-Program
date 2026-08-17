package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SystemAccessSeedMigrationTest {

    @Test
    void systemAccessSeedDefinesMenusPermissionsAndAdminRole() throws IOException {
        String sql = readMigration("/db/migration/V2__system_access_seed.sql");

        assertTrue(sql.contains("INSERT INTO sys_role"));
        assertTrue(sql.contains("'ADMIN', '系统管理员'"));
        assertTrue(sql.contains("INSERT INTO sys_menu"));
        assertTrue(sql.contains("'asset:building',"));
        assertTrue(sql.contains("'system:role',"));
        assertTrue(sql.contains("INSERT INTO sys_permission"));
        assertTrue(sql.contains("'system:role:assign-permission'"));
        assertTrue(sql.contains("'building:view'"));
        assertTrue(sql.contains("INSERT INTO sys_role_permission"));
        assertTrue(sql.contains("INSERT INTO sys_role_menu"));
    }

    @Test
    void systemRoleAccessRepairMigrationGrantsDefaultMenusAndPermissions() throws IOException {
        String sql = readMigration("/db/migration/V7__system_role_access_repair.sql");

        assertTrue(sql.contains("sys_role_menu"));
        assertTrue(sql.contains("sys_role_permission"));
        assertTrue(sql.contains("'ADMIN'"));
        assertTrue(sql.contains("'SUPER_ADMIN'"));
        assertTrue(sql.contains("'OPERATION_MANAGER'"));
        assertTrue(sql.contains("'PROPERTY_MANAGER'"));
    }

    @Test
    void rbacRoleMatrixMigrationAddsOnlyInternalMatrixRolesAndAdminFullAccess() throws IOException {
        String sql = readMigration("/db/migration/V28__rbac_role_matrix_roles.sql");

        assertTrue(sql.contains("'SUPER_ADMIN'"));
        assertTrue(sql.contains("'INVESTMENT_MANAGER'"));
        assertTrue(sql.contains("'ACCOUNTANT'"));
        assertTrue(sql.contains("'CASHIER'"));
        assertTrue(sql.contains("'ENGINEER_MANAGER'"));
        assertTrue(sql.contains("'SECURITY_MANAGER'"));
        assertTrue(sql.contains("'mobile:update'"));
        assertTrue(sql.contains("r.code IN ('ADMIN', 'SUPER_ADMIN')"));
        assertTrue(sql.contains("INSERT INTO sys_role_menu"));
        assertTrue(sql.contains("INSERT INTO sys_role_permission"));
        assertTrue(!sql.contains("'TENANT_ADMIN'"));
        assertTrue(!sql.contains("'TENANT_STAFF'"));
    }

    @Test
    void adminRoleProtectionMigrationRepairsCoreRoleStatusWithoutChangingSchema() throws IOException {
        String sql = readMigration("/db/migration/V34__protect_system_admin_roles.sql");

        assertTrue(sql.contains("code IN ('ADMIN', 'SUPER_ADMIN')"));
        assertTrue(sql.contains("role_type = 'SYSTEM'"));
        assertTrue(sql.contains("status = 'ENABLED'"));
        assertTrue(sql.contains("deleted = 0"));
        assertTrue(!sql.contains("CREATE TABLE"));
        assertTrue(!sql.contains("ALTER TABLE"));
        assertTrue(!sql.contains("DELETE FROM"));
    }

    private String readMigration(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertNotNull(input, path + " migration must exist");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
