package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TenantArchiveV54MigrationTest {

    @Test
    void migrationAddsOnlyCanonicalArchiveFieldsAndSafeUniqueSelectors() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V54__close_tenant_archive_loop.sql"
        ));

        assertTrue(sql.contains("ADD COLUMN short_name"));
        assertTrue(sql.contains("ADD COLUMN invoice_title"));
        assertTrue(sql.contains("ADD COLUMN tax_number"));
        assertTrue(sql.contains("ADD COLUMN service_manager_user_id"));
        assertTrue(sql.contains("ADD COLUMN is_emergency"));
        assertTrue(sql.contains("uk_tenant_contact_active_responsible"));
        assertTrue(sql.contains("uk_tenant_contact_active_emergency"));
        assertTrue(sql.contains("ROW_NUMBER() OVER"));
        assertFalse(sql.contains("ADD COLUMN credit_code_"));
        assertFalse(sql.contains("DELETE FROM"));
        assertFalse(sql.contains("DROP TABLE"));
    }
}
