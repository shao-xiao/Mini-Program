package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class EnergySchemaMigrationTest {

    @Test
    void energyMigrationDefinesMeterReadingTablesAndMenu() throws IOException {
        String sql = readMigration();

        assertTrue(sql.contains("CREATE TABLE energy_meter"), "energy_meter table must exist");
        assertTrue(sql.contains("CREATE TABLE energy_reading"), "energy_reading table must exist");
        assertTrue(sql.contains("meter_no"), "meter number column must exist");
        assertTrue(sql.contains("period_month"), "reading period column must exist");
        assertTrue(sql.contains("'energy:meter'"), "meter management menu must be seeded");
        assertTrue(sql.contains("'/energy/meters'"), "meter management route must be seeded");
        assertTrue(sql.contains("'energy:meter:create'"), "meter create permission must be seeded");
    }

    @Test
    void energyPriceMigrationDefinesPeriodTypeUnitPrice() throws IOException {
        String sql = readMigration("/db/migration/V26__energy_price_setting.sql");

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS energy_price_setting"));
        assertTrue(sql.contains("period_month VARCHAR(7) NOT NULL"));
        assertTrue(sql.contains("meter_type VARCHAR(32) NOT NULL"));
        assertTrue(sql.contains("unit_price DECIMAL(12,4) NOT NULL"));
        assertTrue(sql.contains("uk_energy_price_period_type"));
    }

    private String readMigration() throws IOException {
        return readMigration("/db/migration/V13__energy_meter_reading.sql");
    }

    private String readMigration(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertNotNull(input, path + " migration must exist");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
