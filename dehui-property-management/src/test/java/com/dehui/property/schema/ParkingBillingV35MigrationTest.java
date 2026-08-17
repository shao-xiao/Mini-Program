package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ParkingBillingV35MigrationTest {

    @Test
    void migrationOnlyAddsNullableBillingFields() throws Exception {
        String sql = readMigration();
        String normalized = sql.toLowerCase(Locale.ROOT);

        assertTrue(normalized.contains("alter table parking_bill"));
        assertTrue(normalized.contains("add column billing_month char(7) null"));
        assertTrue(normalized.contains("alter table parking_assignment"));
        assertTrue(normalized.contains("add column monthly_fee decimal(10,2) null"));
        assertFalse(normalized.contains("active_billing_month"));
        assertFalse(normalized.contains("unique key"));
        assertFalse(normalized.contains("create unique index"));
        assertFalse(normalized.contains("update parking_bill"));
        assertFalse(normalized.contains("update parking_assignment"));
        assertFalse(normalized.contains("delete from"));
    }

    private String readMigration() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/db/migration/V35__add_parking_billing_month_and_monthly_fee.sql")) {
            assertNotNull(input, "V35 parking billing migration must exist");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
