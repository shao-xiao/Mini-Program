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
        String sql = readMigration("/db/migration/V5__tenant_contact_fields.sql");

        assertTrue(sql.contains("ADD COLUMN contact_person"), "tenant contact person column is required");
        assertTrue(sql.contains("ADD COLUMN contact_email"), "tenant contact email column is required");
        assertTrue(sql.contains("ADD COLUMN email"), "tenant contact email column is required");
    }

    @Test
    void migrationAddsTenantContactPasswordHashForMiniProgramBinding() throws IOException {
        String sql = readMigration("/db/migration/V8__tenant_contact_password_hash.sql");

        assertTrue(sql.contains("ADD COLUMN password_hash"), "tenant contact password hash column is required");
        assertTrue(sql.contains("UPDATE tenant_contact"), "existing tenant contacts should receive a default hash");
        assertTrue(sql.contains("password_hash IS NULL"), "migration should only backfill missing hashes");
    }

    private String readMigration(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertNotNull(input, path + " migration must exist");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
