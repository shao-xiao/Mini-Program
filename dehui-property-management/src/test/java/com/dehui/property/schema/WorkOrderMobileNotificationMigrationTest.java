package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WorkOrderMobileNotificationMigrationTest {

    @Test
    void migrationCreatesWorkOrderMobileNotificationTable() throws IOException {
        String sql = new String(getClass().getResourceAsStream("/db/migration/V19__work_order_mobile_notification.sql").readAllBytes(), StandardCharsets.UTF_8);

        assertTrue(sql.contains("CREATE TABLE work_order_mobile_notification"));
        assertTrue(sql.contains("recipient_user_id"));
        assertTrue(sql.contains("read_status"));
        assertTrue(sql.contains("idx_wom_notify_recipient"));
    }
}
