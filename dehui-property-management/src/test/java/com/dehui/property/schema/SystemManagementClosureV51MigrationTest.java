package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SystemManagementClosureV51MigrationTest {

    @Test
    void migrationAddsIdentityClosureFieldsAndAuditWithoutDestructiveHistoryCleanup() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V51__simplify_system_management_identity_closure.sql"
        ));

        assertTrue(sql.contains("web_login_enabled"));
        assertTrue(sql.contains("primary_role_id"));
        assertTrue(sql.contains("last_login_at"));
        assertTrue(sql.contains("ADD COLUMN position"));
        assertTrue(sql.contains("phone_verified_at"));
        assertTrue(sql.contains("CREATE TABLE mobile_identity_audit"));
        assertTrue(sql.contains("WHEN 'system:user' THEN 101"));
        assertTrue(sql.contains("WHEN 'system:role' THEN 102"));
        assertTrue(sql.contains("WHEN 'system:mobile-user' THEN 103"));
        assertFalse(sql.contains("DROP TABLE"));
        assertFalse(sql.contains("TRUNCATE TABLE"));
        assertFalse(sql.contains("DELETE FROM sys_role_permission"));
        assertFalse(sql.contains("DELETE FROM sys_role_menu"));
    }
}
