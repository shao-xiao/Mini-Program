package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BuildingFloorTotalAreaMigrationTest {

    @Test
    void migrationAddsTotalAreaToBuildingFloor() throws IOException {
        String sql = new String(getClass().getResourceAsStream("/db/migration/V20__building_floor_total_area.sql").readAllBytes(), StandardCharsets.UTF_8);

        assertTrue(sql.contains("ALTER TABLE building_floor"));
        assertTrue(sql.contains("ADD COLUMN total_area"));
        assertTrue(sql.contains("DECIMAL(12,2)"));
    }
}
