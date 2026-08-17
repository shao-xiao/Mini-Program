package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class EquipmentSchemaMigrationTest {

    @Test
    void equipmentMigrationDefinesEquipmentTable() throws IOException {
        String sql = readMigration();

        assertTrue(sql.contains("CREATE TABLE equipment"), "equipment table must exist");
        assertTrue(sql.contains("equipment_name"), "equipment_name column must exist");
        assertTrue(sql.contains("equipment_code"), "equipment_code column must exist");
        assertTrue(sql.contains("status"), "status column must exist");
        assertTrue(sql.contains("deleted"), "logical delete column must exist");
    }

    private String readMigration() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/db/migration/V12__equipment_ledger.sql")) {
            assertNotNull(input, "equipment migration must exist");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
