package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MobileStaffIdentityHardeningV64MigrationTest {

    @Test
    void migrationSeparatesWechatVerificationFromLegacyTimestampsAndPreventsSharedActiveBindings()
            throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V64__harden_mobile_staff_identity.sql"
        ));

        assertTrue(sql.contains("phone_verification_source"));
        assertTrue(sql.contains("DEFAULT 'UNVERIFIED'"));
        assertTrue(sql.contains("active_tenant_contact_guard"));
        assertTrue(sql.contains("active_sys_user_guard"));
        assertTrue(sql.contains("UNIQUE KEY uk_mobile_bind_active_tenant_contact"));
        assertTrue(sql.contains("UNIQUE KEY uk_mobile_bind_active_sys_user"));
        assertFalse(sql.contains("UPDATE mobile_user"));
        assertFalse(sql.contains("DELETE FROM"));
        assertFalse(sql.contains("TRUNCATE"));
        assertFalse(sql.contains("DROP TABLE"));
    }
}
