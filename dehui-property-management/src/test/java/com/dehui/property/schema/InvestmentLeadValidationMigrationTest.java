package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class InvestmentLeadValidationMigrationTest {

    @Test
    void migrationAddsIntendedRoomForViewingAppointmentValidation() throws Exception {
        String sql = readMigration("/db/migration/V14__investment_lead_validation_fields.sql");
        assertTrue(sql.contains("ADD COLUMN intended_room"), "investment lead should store intended room separately");
        assertTrue(sql.contains("VARCHAR(64)"), "intended room field should have a bounded length");
    }

    private String readMigration(String path) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertNotNull(input, path + " must exist");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
