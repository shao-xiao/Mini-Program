package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ParkingSpaceAreaTypeMigrationTest {

    @Test
    void migrationAddsParkingAreaAndTypeOptions() throws IOException {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V21__parking_space_area_and_type_options.sql"),
                StandardCharsets.UTF_8
        );

        assertTrue(sql.contains("ALTER TABLE parking_space"));
        assertTrue(sql.contains("ADD COLUMN area"));
        assertTrue(sql.contains("CREATE TABLE parking_space_type_option"));
        assertTrue(sql.contains("'TRUCK', '货车车位'"));
        assertTrue(sql.contains("'DEDICATED', '专用车位'"));
        assertTrue(sql.contains("'MONTHLY', '月租车位'"));
    }
}
