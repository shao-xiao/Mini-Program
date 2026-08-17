package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class InvestmentAnnouncementNavigationV41MigrationTest {

    @Test
    void migrationCreatesThreeUnifiedMenusAndDisablesLegacyNavigationOnly() throws Exception {
        String sql = new String(
                getClass().getResourceAsStream("/db/migration/V41__consolidate_investment_announcement_navigation.sql").readAllBytes(),
                StandardCharsets.UTF_8
        );

        assertTrue(sql.contains("'investment-announcement:manage'"));
        assertTrue(sql.contains("'investment-announcement:leads'"));
        assertTrue(sql.contains("'investment-announcement:announcements'"));
        assertTrue(sql.contains("'/investment/manage'"));
        assertTrue(sql.contains("'/investment/announcements'"));
        assertTrue(sql.contains("legacy_menu.code IN ('lease:investment-content', 'lease:investment-listing')"));
        assertTrue(sql.contains("legacy_menu.code = 'operation:announcement'"));
        assertTrue(sql.contains("SET rm.status = 'DISABLED'"));
        assertTrue(sql.contains("UPDATE sys_menu"));
        assertFalse(sql.contains("DELETE FROM"));
        assertFalse(sql.contains("DROP TABLE"));
        assertFalse(sql.contains("sys_role_permission"));
    }
}
