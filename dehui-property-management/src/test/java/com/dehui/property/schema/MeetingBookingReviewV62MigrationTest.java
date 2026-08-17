package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MeetingBookingReviewV62MigrationTest {

    @Test
    void migrationAddsReviewResultWithoutRewritingHistoricalBookings() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V62__add_meeting_booking_review_result.sql"
        ));

        assertTrue(sql.contains("ALTER TABLE meeting_booking"));
        assertTrue(sql.contains("ADD COLUMN review_reply"));
        assertTrue(sql.contains("ADD COLUMN reviewed_by"));
        assertTrue(sql.contains("ADD COLUMN reviewed_at"));
        assertFalse(sql.contains("UPDATE meeting_booking"));
        assertFalse(sql.contains("DELETE FROM meeting_booking"));
        assertFalse(sql.contains("TRUNCATE"));
        assertFalse(sql.contains("DROP TABLE"));
    }
}
