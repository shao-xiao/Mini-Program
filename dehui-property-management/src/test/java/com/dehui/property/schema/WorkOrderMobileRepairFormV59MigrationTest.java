package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class WorkOrderMobileRepairFormV59MigrationTest {

    @Test
    void addsReportSnapshotsAndAlignsTheMiniProgramFormWithoutRelationshipIds() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V59__refine_mobile_work_order_report_form.sql"
        )).replace("`", "").replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);

        assertTrue(sql.contains("add column reported_company_name varchar(128)"));
        assertTrue(sql.contains("add column reported_at datetime"));
        assertTrue(sql.contains("update work_order work_record left join tenant tenant_record"));
        assertTrue(sql.contains("insert into module_field_setting"));
        assertTrue(sql.contains("'companyname'"));
        assertTrue(sql.contains("'reportedat'"));
        assertTrue(sql.contains("'problemcategory'"));
        assertFalse(sql.contains("add column tenant_id"));
        assertFalse(sql.contains("add column mobile_user_id"));
        assertFalse(sql.contains("add column sys_user_id"));
    }
}
