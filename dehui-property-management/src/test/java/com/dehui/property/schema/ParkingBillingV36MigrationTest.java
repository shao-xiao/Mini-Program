package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ParkingBillingV36MigrationTest {

    @Test
    void migrationAddsForwardOnlyActiveMonthUniqueness() throws Exception {
        String sql = readMigration();
        String normalized = sql.toLowerCase(Locale.ROOT);

        assertTrue(normalized.contains("add column active_billing_month char(7)"));
        assertTrue(normalized.contains("generated always as"));
        assertTrue(normalized.contains("deleted = 0"));
        assertTrue(normalized.contains("status in ('active', 'enabled')"));
        assertTrue(normalized.contains("then billing_month"));
        assertTrue(normalized.contains("else null"));
        assertTrue(normalized.contains("unique key uk_parking_bill_active_month"));
        assertTrue(normalized.contains("(assignment_id, active_billing_month)"));
        assertFalse(normalized.contains("update parking_bill"));
        assertFalse(normalized.contains("delete from"));
        assertFalse(normalized.contains("insert into"));
        assertFalse(normalized.contains("due_date"));
        assertFalse(normalized.contains("billing_month char(7) not null"));
    }

    private String readMigration() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/db/migration/V36__enforce_parking_bill_active_month_uniqueness.sql")) {
            assertNotNull(input, "V36 parking billing migration must exist");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
