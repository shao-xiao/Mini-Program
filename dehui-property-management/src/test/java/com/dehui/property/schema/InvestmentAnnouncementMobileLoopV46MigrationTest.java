package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class InvestmentAnnouncementMobileLoopV46MigrationTest {

    @Test
    void migrationClosesFourBusinessDataLoopsWithoutDeletingHistory() throws Exception {
        String path = "/db/migration/V46__close_investment_announcement_mobile_loop.sql";
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertNotNull(input, path + " migration must exist");
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(sql.contains("ALTER TABLE investment_display"));
            assertTrue(sql.contains("public_visible"));
            assertTrue(sql.contains("WHERE section_key = 'notice'"));
            assertTrue(sql.contains("SET publish_status = 'DISABLED'"));

            assertTrue(sql.contains("ALTER TABLE investment_room_listing"));
            assertTrue(sql.contains("display_area_snapshot"));
            assertTrue(sql.contains("JOIN building_room room_record"));
            assertTrue(sql.contains("is_reservable"));

            assertTrue(sql.contains("ALTER TABLE investment_lead"));
            assertTrue(sql.contains("mobile_user_id"));
            assertTrue(sql.contains("listing_title_snapshot"));
            assertTrue(sql.contains("CREATE TABLE investment_lead_status_history"));

            assertTrue(sql.contains("ALTER TABLE notice"));
            assertTrue(sql.contains("carousel_sort"));
            assertTrue(sql.contains("visibility_scope"));

            assertFalse(sql.contains("DELETE FROM"));
            assertFalse(sql.contains("TRUNCATE"));
            assertFalse(sql.contains("DROP TABLE"));
        }
    }
}
