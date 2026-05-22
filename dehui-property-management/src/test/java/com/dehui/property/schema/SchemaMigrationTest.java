package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchemaMigrationTest {

    @Test
    void baselineMigrationDefinesProductionTables() throws IOException {
        String sql = readMigration();

        List<String> requiredTables = List.of(
                "sys_user",
                "sys_role",
                "mobile_user",
                "wechat_identity",
                "building_room",
                "tenant",
                "contract",
                "bill",
                "bill_payment_record",
                "meeting_booking",
                "parking_space",
                "work_order",
                "visitor_appointment",
                "investment_lead",
                "staff_checkin",
                "file_upload",
                "log_operation",
                "notice"
        );

        for (String table : requiredTables) {
            assertTrue(sql.contains("CREATE TABLE " + table), "missing table " + table);
        }

        assertTrue(sql.contains("ENGINE=InnoDB"));
        assertTrue(sql.contains("COMMENT="));
    }

    private String readMigration() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/db/migration/V1__baseline_schema.sql")) {
            assertNotNull(input, "baseline migration must exist");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
