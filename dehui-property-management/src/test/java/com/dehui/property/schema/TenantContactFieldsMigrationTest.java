package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TenantContactFieldsMigrationTest {

    @Test
    void migrationAddsTenantContactPersonAndEmailFields() throws IOException {
        String sql = readMigration();

        assertTrue(sql.contains("ADD COLUMN contact_person"), "tenant contact person column is required");
        assertTrue(sql.contains("ADD COLUMN contact_email"), "tenant contact email column is required");
        assertTrue(sql.contains("ADD COLUMN email"), "tenant contact email column is required");
    }

    private String readMigration() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/db/migration/V5__tenant_contact_fields.sql")) {
            assertNotNull(input, "tenant contact fields migration must exist");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
