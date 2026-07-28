package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class VisitorReviewQrMigrationTest {

    @Test
    void migrationAddsReviewAuditAndProtectedQrReferences() throws IOException {
        String sql = readMigration("/db/migration/V58__visitor_review_and_access_qr.sql");

        for (String column : new String[]{
                "reviewed_by",
                "reviewed_at",
                "review_remark",
                "qr_code_file_id",
                "qr_uploaded_by",
                "qr_uploaded_at"
        }) {
            assertTrue(sql.contains("ADD COLUMN " + column), column + " must be added by Flyway");
        }
        assertTrue(sql.contains("idx_visitor_review_queue"));
        assertTrue(sql.contains("idx_visitor_qr_file"));
    }

    private String readMigration(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertNotNull(input, path + " migration must exist");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
