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
        String sql = readMigration();

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

    private String readMigration() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/db/migration/V2__system_access_seed.sql")) {
            assertNotNull(input, "system access seed migration must exist");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
