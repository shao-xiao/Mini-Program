package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class NoticeHomeCarouselMigrationTest {

    @Test
    void noticeHomeCarouselMigrationAddsFlagAndIndex() throws IOException {
        String sql = readMigration("/db/migration/V10__notice_home_carousel.sql");

        assertTrue(sql.contains("ALTER TABLE notice"), "migration should alter notice table");
        assertTrue(sql.contains("ADD COLUMN show_on_home"), "notice should store whether it appears on mini-program home carousel");
        assertTrue(sql.contains("idx_notice_home_carousel"), "home carousel query should have an index");
    }

    private String readMigration(String path) throws IOException {
        try (var input = getClass().getResourceAsStream(path)) {
            assertNotNull(input, path + " migration must exist");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
