package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UnifiedModuleFieldSchemaV50MigrationTest {

    @Test
    void migrationAddsOnlySchemaSettingsAndProtectedFileOwnership() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V50__unify_module_field_schema_surfaces.sql"
        ));
        assertTrue(sql.contains("CREATE TABLE module_field_setting"));
        assertTrue(sql.contains("validation_json JSON"));
        assertTrue(sql.contains("surfaces_json JSON"));
        assertTrue(sql.contains("owner_mobile_user_id"));
        assertTrue(sql.contains("workspace_key"));
        assertFalse(sql.contains("DROP TABLE"));
        assertFalse(sql.contains("DELETE FROM"));
    }
}
