package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BootstrapSuperAdminV52MigrationTest {

    @Test
    void migrationBootstrapsOnlyWhenNoEnabledSuperAdminExists() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V52__ensure_bootstrap_super_admin.sql"
        ));

        assertTrue(sql.contains("code = 'SUPER_ADMIN'"));
        assertTrue(sql.contains("SET @needs_super_admin"));
        assertTrue(sql.contains("WHERE @needs_super_admin = 1"));
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(sql.contains("SET primary_role_id = @super_role_id"));
        assertFalse(sql.contains("DELETE FROM"));
        assertFalse(sql.contains("DROP TABLE"));
    }
}
