package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class InvestmentContentSectionsMigrationTest {

    @Test
    void migrationAddsMiniProgramSectionFields() throws IOException {
        String sql = readMigration();

        assertTrue(sql.contains("ADD COLUMN section_key"), "section_key column is required");
        assertTrue(sql.contains("ADD COLUMN subtitle"), "subtitle column is required");
        assertTrue(sql.contains("ADD COLUMN image_url"), "image_url column is required");
        assertTrue(sql.contains("idx_invest_display_section_publish_sort"), "section publish sort index is required");
        assertTrue(sql.contains("UPDATE investment_display"), "old rows must be migrated");
    }

    private String readMigration() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/db/migration/V4__investment_content_sections.sql")) {
            assertNotNull(input, "investment content section migration must exist");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
