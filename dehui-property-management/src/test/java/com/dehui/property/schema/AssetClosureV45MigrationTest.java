package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class AssetClosureV45MigrationTest {

    @Test
    void migrationAddsHistoricalBindingsAndImmutableChargeSnapshots() throws Exception {
        String normalized = readMigration().toLowerCase(Locale.ROOT);

        assertTrue(normalized.contains("add column property_fee_area decimal(10,2)"));
        assertTrue(normalized.contains("add unique key uk_room_floor_no (floor_id, room_no, deleted)"));
        assertTrue(normalized.contains("create table energy_meter_binding"));
        assertTrue(normalized.contains("effective_from date not null"));
        assertTrue(normalized.contains("effective_to date null"));
        assertTrue(normalized.contains("create table energy_reading_audit"));
        assertTrue(normalized.contains("add column unit_price_snapshot decimal(10,2)"));
        assertTrue(normalized.contains("create table meeting_rate_rule"));
        assertTrue(normalized.contains("add column unit_price_snapshot decimal(10,2) not null default 0"));
        assertTrue(normalized.contains("create table asset_charge_basis"));
        assertTrue(normalized.contains("unique key uk_asset_charge_basis_source_period"));
        assertTrue(normalized.contains("snapshot_json json"));
    }

    @Test
    void migrationDoesNotRewriteOrDeleteFinancialBills() throws Exception {
        String normalized = readMigration().toLowerCase(Locale.ROOT);

        assertFalse(normalized.contains("delete from bill"));
        assertFalse(normalized.contains("truncate"));
        assertFalse(normalized.contains("drop table"));
        assertFalse(normalized.contains("update bill "));
    }

    private String readMigration() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/db/migration/V45__close_asset_billing_basis_loops.sql")) {
            assertNotNull(input, "V45 asset closure migration must exist");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
