package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ApprovedBillDeletionPermissionV65MigrationTest {

    @Test
    void migrationAddsTheCapabilityWithoutAssigningItToAnyRole() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V65__add_approved_bill_record_deletion_permission.sql"
        )).toLowerCase(Locale.ROOT);

        assertTrue(sql.contains("'bill:delete-approved'"));
        assertTrue(sql.contains("insert into sys_permission"));
        assertFalse(sql.contains("sys_role_permission"));
        assertFalse(sql.contains("sys_user_role"));
        assertFalse(sql.contains("sys_role_menu"));
    }
}
