package com.dehui.property.modules.mobile;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MobileStaffMeetingReviewContractTest {

    @Test
    void staffConfirmationPersistsServerOwnedReviewResult() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/dehui/property/modules/mobile/controller/MobileStaffController.java"
        ));

        assertTrue(source.contains("booking.review_reply AS reviewReply"));
        assertTrue(source.contains("booking.reviewed_at AS reviewedAt"));
        assertTrue(source.contains("review_reply = ?"));
        assertTrue(source.contains("reviewed_by = ?"));
        assertTrue(source.contains("reviewed_at = NOW()"));
        assertTrue(source.contains("\"预约已审核通过\""));
        assertTrue(source.contains("case \"COMPLETED\" -> Set.of(\"CONFIRMED\")"));
    }

    @Test
    void workbenchExcludesRejectedMeetings() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/dehui/property/modules/mobile/controller/MobileStaffController.java"
        ));

        assertTrue(source.contains("booking_status NOT IN ('CANCELLED','REJECTED')"));
    }
}
