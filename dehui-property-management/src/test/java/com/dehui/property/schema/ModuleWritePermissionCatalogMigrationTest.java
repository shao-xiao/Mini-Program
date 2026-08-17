package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class ModuleWritePermissionCatalogMigrationTest {

    @Test
    void registersMissingConcreteWritePermissionsWithoutGrantingRoles() throws IOException {
        String sql = new ClassPathResource(
                "db/migration/V43__complete_module_write_permission_catalog.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertTrue(sql.contains("'inspection:delete'"));
        assertTrue(sql.contains("'bill:update'"));
        assertTrue(sql.contains("'bill:delete'"));
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"));
        assertFalse(sql.contains("INSERT INTO sys_role_permission"));
        assertFalse(sql.contains("UPDATE sys_role_permission"));
    }
}
