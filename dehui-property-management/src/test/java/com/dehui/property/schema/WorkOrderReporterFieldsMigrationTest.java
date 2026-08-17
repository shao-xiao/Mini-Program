package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WorkOrderReporterFieldsMigrationTest {

    @Test
    void workOrderReporterMigrationAddsNameAndPhoneColumns() throws IOException {
        String sql = readMigration();

        assertTrue(sql.contains("reporter_name"), "work order reporter name column is required");
        assertTrue(sql.contains("reporter_phone"), "work order reporter phone column is required");
        assertTrue(sql.contains("UPDATE work_order"), "existing rows should be backfilled when possible");
    }

    private String readMigration() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/db/migration/V15__work_order_reporter_fields.sql")) {
            assertNotNull(input, "work order reporter migration must exist");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
