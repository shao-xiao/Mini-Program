package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BillEditableFieldsMigrationTest {

    @Test
    void billEditableFieldsMigrationAddsTitleAndPeriodFields() throws IOException {
        String sql = readMigration("/db/migration/V9__bill_editable_fields.sql");

        assertTrue(sql.contains("ALTER TABLE bill"), "migration should alter bill table");
        assertTrue(sql.contains("ADD COLUMN title"), "bill title should be persisted");
        assertTrue(sql.contains("ADD COLUMN period_start"), "bill period start should be persisted");
        assertTrue(sql.contains("ADD COLUMN period_end"), "bill period end should be persisted");
        assertTrue(sql.contains("idx_bill_period"), "period fields should be indexed for bill queries");
    }

    private String readMigration(String path) throws IOException {
        try (var input = getClass().getResourceAsStream(path)) {
            assertNotNull(input, path + " migration must exist");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
