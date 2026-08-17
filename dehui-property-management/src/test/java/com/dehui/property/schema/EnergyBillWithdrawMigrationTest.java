package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class EnergyBillWithdrawMigrationTest {

    @Test
    void migrationCreatesEnergyBillOperationLog() throws Exception {
        String sql = readMigration("/db/migration/V25__energy_bill_withdraw_log.sql");

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS energy_bill_operation_log"));
        assertTrue(sql.contains("reading_id BIGINT NOT NULL"));
        assertTrue(sql.contains("bill_id BIGINT DEFAULT NULL"));
        assertTrue(sql.contains("action VARCHAR(64) NOT NULL"));
        assertTrue(sql.contains("result_status VARCHAR(32) NOT NULL"));
        assertTrue(sql.contains("idx_energy_bill_log_reading"));
        assertTrue(sql.contains("idx_energy_bill_log_bill"));
    }

    private String readMigration(String path) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertNotNull(input, path + " migration must exist");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
