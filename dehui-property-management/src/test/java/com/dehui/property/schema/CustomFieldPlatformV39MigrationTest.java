package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CustomFieldPlatformV39MigrationTest {

    private static final Path MIGRATIONS = Path.of("src", "main", "resources", "db", "migration");

    @Test
    void v39UpgradesV18InPlaceWithTypedStorageAndHistory() throws Exception {
        String sql = migration();
        assertTrue(sql.contains("alter table custom_field_definition"));
        assertTrue(sql.contains("workspace_key"));
        assertTrue(sql.contains("entity_type"));
        assertTrue(sql.contains("config_json"));
        assertTrue(sql.contains("version bigint"));

        assertTrue(sql.contains("alter table custom_field_value"));
        for (String column : new String[]{
                "field_id", "entity_id", "value_text", "value_long_text", "value_number",
                "value_decimal", "value_date", "value_datetime", "value_boolean"
        }) {
            assertTrue(sql.contains(column), "V39 must add " + column);
        }
        assertTrue(sql.contains("decimal(18,2)"));
        assertTrue(sql.contains("create table custom_field_option"));
        assertTrue(sql.contains("create table custom_field_definition_history"));
    }

    @Test
    void v39AddsActiveValueUniquenessAndTypedQueryIndexes() throws Exception {
        String sql = migration();
        assertTrue(sql.contains("active_entity_id"));
        assertTrue(sql.contains("unique key uk_custom_field_value_active_entity"));
        for (String column : new String[]{
                "value_text", "value_number", "value_decimal", "value_date", "value_datetime", "value_boolean"
        }) {
            assertTrue(sql.contains("field_id, " + column), "missing typed index for " + column);
        }
    }

    @Test
    void v39SeedsExplicitPermissionsWithoutChangingBusinessTables() throws Exception {
        String sql = migration();
        assertTrue(sql.contains("'custom-field:view'"));
        assertTrue(sql.contains("'custom-field:manage'"));
        assertTrue(sql.contains("'admin', 'super_admin'"));

        assertFalse(sql.contains("drop table"));
        assertFalse(sql.contains("truncate "));
        for (String businessTable : new String[]{"bill", "building", "contract", "room", "tenant"}) {
            assertFalse(sql.contains("alter table " + businessTable + " "),
                    "V39 must not alter business table " + businessTable);
        }
    }

    private static String migration() throws Exception {
        Path path;
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            path = files.filter(file -> file.getFileName().toString().startsWith("V39__"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("V39 custom-field platform migration must exist"));
        }
        return Files.readString(path).replace("`", "").replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
