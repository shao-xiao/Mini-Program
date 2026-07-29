package com.dehui.property.modules.mobile;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MobileStaffVisitorReviewContractTest {

    @Test
    void staffVisitorRoutesReuseApprovalServiceAndStaffSystemIdentity() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/dehui/property/modules/mobile/controller/MobileStaffController.java"
        ));

        assertTrue(source.contains("@PostMapping(\"/mobile/staff/visitors/{id}/approve\")"));
        assertTrue(source.contains("@PostMapping(\"/mobile/staff/visitors/{id}/reject\")"));
        assertTrue(source.contains("@PostMapping(\"/mobile/staff/visitors/{id}/qr-code\")"));
        assertTrue(source.contains("require(principal, \"visitor.staff.verify\")"));
        assertTrue(source.contains("approveByStaff("));
        assertTrue(source.contains("rejectByStaff("));
        assertTrue(source.contains("attachStaffQrCode("));
        assertTrue(source.contains("staff(principal)"));
    }

    @Test
    void visitorDetailExposesAuditAndProtectedQrMetadata() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/dehui/property/modules/mobile/controller/MobileStaffController.java"
        ));

        assertTrue(source.contains("appointment.review_remark AS reviewRemark"));
        assertTrue(source.contains("appointment.reviewed_at AS reviewedAt"));
        assertTrue(source.contains("appointment.qr_code_file_id AS qrCodeFileId"));
        assertTrue(source.contains("qr_file.original_name AS qrCodeFileName"));
        assertTrue(source.contains("CONCAT('/api/mobile/files/', appointment.qr_code_file_id)"));
        assertTrue(source.contains("appointment.appointment_status = 'PENDING'"));
    }
}
