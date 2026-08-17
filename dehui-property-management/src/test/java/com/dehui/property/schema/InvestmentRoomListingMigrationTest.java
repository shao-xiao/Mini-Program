package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class InvestmentRoomListingMigrationTest {

    @Test
    void migrationCreatesInvestmentRoomListingsAndImages() throws Exception {
        String sql = readMigration("/db/migration/V24__investment_room_listing.sql");

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS investment_room_listing"));
        assertTrue(sql.contains("room_id BIGINT NOT NULL"));
        assertTrue(sql.contains("publish_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT'"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS investment_room_image"));
        assertTrue(sql.contains("image_url VARCHAR(500) NOT NULL"));
        assertTrue(sql.contains("ADD COLUMN room_id BIGINT DEFAULT NULL"));
        assertTrue(sql.contains("ADD COLUMN listing_id BIGINT DEFAULT NULL"));
        assertTrue(sql.contains("lease:investment-listing"));
    }

    private String readMigration(String path) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertNotNull(input, path + " migration must exist");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
