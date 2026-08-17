package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ParkingBillingV36OperationalScriptsTest {

    @Test
    void localResetIsFailClosedAndLimitedToParkingTestData() throws Exception {
        String sql = readRootFile("scripts/dev/reset-local-parking-test-data.sql");
        String normalized = sql.toLowerCase(Locale.ROOT);

        assertTrue(normalized.contains("local development only"));
        assertTrue(normalized.contains("dehui_property_dev"));
        assertTrue(normalized.contains("@can_reset"));
        assertTrue(normalized.contains("@snapshot_matches"));
        assertTrue(normalized.contains("@external_fk_count"));
        assertTrue(normalized.contains("start transaction"));
        assertTrue(normalized.contains("update parking_space"));
        assertTrue(normalized.contains("delete from parking_bill"));
        assertTrue(normalized.contains("delete from parking_assignment"));
        assertTrue(normalized.contains("commit"));
        assertFalse(normalized.contains("truncate"));
        assertFalse(normalized.contains("alter table"));
        assertFalse(normalized.contains("delete from tenant"));
        assertFalse(normalized.contains("delete from contract"));
        assertFalse(normalized.contains("delete from bill"));
    }

    @Test
    void productionPrecheckContainsOnlyReadOnlyStatements() throws Exception {
        String sql = readRootFile("scripts/audit/parking-v36-production-precheck.sql");
        String withoutComments = sql.replaceAll("(?m)^\\s*--.*$", "");

        Arrays.stream(withoutComments.split(";"))
                .map(String::trim)
                .filter(statement -> !statement.isEmpty())
                .forEach(statement -> assertTrue(
                        statement.regionMatches(true, 0, "SELECT", 0, "SELECT".length())
                                || statement.regionMatches(true, 0, "SHOW", 0, "SHOW".length())
                                || statement.regionMatches(true, 0, "EXPLAIN", 0, "EXPLAIN".length()),
                        () -> "Production precheck contains a non-read-only statement: " + statement
                ));
    }

    private String readRootFile(String relativePath) throws Exception {
        Path current = Path.of("").toAbsolutePath().normalize();
        Path root = current.getFileName().toString().equals("dehui-property-management")
                ? current.getParent()
                : current;
        return Files.readString(root.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
