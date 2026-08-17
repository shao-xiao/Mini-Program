package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MeetingBookingInformationV57MigrationTest {

    @Test
    void migrationAddsCanonicalDepartmentWithoutRewritingHistory() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V57__complete_meeting_booking_information.sql"
        ));

        assertTrue(sql.contains("ALTER TABLE meeting_booking"));
        assertTrue(sql.contains("ADD COLUMN department_name"));
        assertFalse(sql.contains("DELETE FROM"));
        assertFalse(sql.contains("TRUNCATE"));
        assertFalse(sql.contains("DROP TABLE"));
    }
}
