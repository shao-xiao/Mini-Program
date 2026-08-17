package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RbacWildcardGovernanceMigrationTest {

    @Test
    void migrationRebindsTechAndRemovesBusinessRoleWildcards() throws IOException {
        String sql = readMigration("/db/migration/V32__rbac_wildcard_governance.sql");

        assertTrue(sql.contains("username = 'tech'"));
        assertTrue(sql.contains("code = 'ENGINEER'"));
        assertTrue(sql.contains("INSERT INTO sys_user_role"));
        assertTrue(sql.contains("INSERT INTO sys_role_permission"));
        assertTrue(sql.contains("r.code <> 'SUPER_ADMIN'"));
        assertTrue(sql.contains("concrete_permission.code NOT LIKE '%:*'"));
        assertTrue(sql.contains("DELETE rp"));
        assertTrue(!sql.contains("CREATE TABLE"));
        assertTrue(!sql.contains("ALTER TABLE"));
    }

    @Test
    void followUpMigrationRegistersTheExistingWorkOrderUpdateCode() throws IOException {
        String sql = readMigration("/db/migration/V33__workorder_update_permission.sql");

        assertTrue(sql.contains("'workorder:update'"));
        assertTrue(sql.contains("p.code IN ('workorder:assign', 'workorder:close')"));
        assertTrue(sql.contains("INSERT INTO sys_role_permission"));
        assertTrue(!sql.contains("CREATE TABLE"));
        assertTrue(!sql.contains("ALTER TABLE"));
    }

    private String readMigration(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertNotNull(input, path + " migration must exist");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
