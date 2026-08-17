package com.dehui.property.modules.mobile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PhaseTwoTenantBusinessContractTest {

    @Test
    void migrationAddsCanonicalFieldsWithoutDeletingHistory() throws IOException {
        String sql = read("src/main/resources/db/migration/V48__close_tenant_mobile_business_loop.sql");
        assertTrue(sql.contains("ADD COLUMN order_type"));
        assertTrue(sql.contains("ADD COLUMN location"));
        assertTrue(sql.contains("ADD COLUMN description"));
        assertTrue(sql.contains("ADD COLUMN visit_company"));
        assertTrue(sql.contains("ADD COLUMN visit_reason"));
        assertFalse(sql.contains("DELETE FROM"));
        assertFalse(sql.contains("DROP TABLE"));
    }

    @Test
    void myBusinessSeparatesCompanyAndSelfSummaries() throws IOException {
        String source = read("src/main/java/com/dehui/property/modules/mobile/controller/MobileTenantBusinessController.java");
        assertTrue(source.contains("\"TENANT_COMPANY\" : \"SELF\""));
        assertTrue(source.contains("submitter_mobile_user_id = ?"));
        assertTrue(source.contains("applicant_mobile_user_id = ?"));
    }

    @Test
    void billListAndDetailAreTenantScopedAndPaginated() throws IOException {
        String source = read("src/main/java/com/dehui/property/modules/bill/controller/BillController.java");
        assertTrue(source.contains("@GetMapping(\"/mobile/bills/{id}\")"));
        assertTrue(source.contains("b.tenant_id = ?"));
        assertTrue(source.contains("JdbcPagination.query"));
        assertTrue(source.contains("Unsupported mobile bill status"));
        assertTrue(source.contains("Unsupported mobile bill category"));
        assertTrue(source.contains("payment_record.paid_at AS paidAt"));
        assertTrue(source.contains("WHEN b.pay_status NOT IN ('PAID', 'CANCELLED') AND b.due_date < CURRENT_DATE THEN 0"));
    }

    @Test
    void contractListDetailAndDownloadRemainTenantScoped() throws IOException {
        String source = read("src/main/java/com/dehui/property/modules/contract/controller/ContractController.java");
        assertTrue(source.contains("@GetMapping(\"/mobile/contracts/{id}\")"));
        assertTrue(source.contains("c.tenant_id = ?"));
        assertTrue(source.contains("contractDownloadUrl"));
    }

    @Test
    void energyUsesServerSettlementAmountsAndScope() throws IOException {
        String source = read("src/main/java/com/dehui/property/modules/mobile/controller/MobileAssetController.java");
        assertTrue(source.contains("settlement_amount"));
        assertTrue(source.contains("/mobile/energy/readings"));
        assertTrue(source.contains("/mobile/energy/details/{id}"));
    }

    @Test
    void parkingCompanyMineAndDetailUseIndependentScopeChecks() throws IOException {
        String source = read("src/main/java/com/dehui/property/modules/mobile/controller/MobileAssetController.java");
        assertTrue(source.contains("/mobile/parking/company"));
        assertTrue(source.contains("/mobile/parking/mine"));
        assertTrue(source.contains("/mobile/parking/{id}"));
        assertTrue(source.contains("tenant_contact_id = ?"));
        assertTrue(source.contains("\"summary\", parkingSummary"));
        assertTrue(source.contains("AS fixedCount"));
        assertTrue(source.contains("AS unboundCount"));
        assertTrue(source.contains("assignment_record.tenant_contact_id IS NULL"));
        assertTrue(source.contains("assignment_record.mobile_user_id IS NULL"));
    }

    @Test
    void publicMeetingProjectionNeverSelectsTenantContactOrPrice() throws IOException {
        String source = read("src/main/java/com/dehui/property/modules/meeting/controller/MeetingController.java");
        String projection = source.substring(
                source.indexOf("@GetMapping(\"/mobile/public/meeting-rooms\")"),
                source.indexOf("@GetMapping({\"/meeting-rooms\"")
        );
        assertFalse(projection.contains("contact_phone"));
        assertFalse(projection.contains("tenant_id"));
        assertFalse(projection.contains("hourly_price"));
    }

    @Test
    void meetingCreateDerivesContactAndKeepsTransactionalConflictCheck() throws IOException {
        String source = read("src/main/java/com/dehui/property/modules/meeting/controller/MeetingController.java");
        assertTrue(source.contains("mobileBookingContact(principal.userId())"));
        assertTrue(source.contains("FOR UPDATE"));
        assertTrue(source.contains("start_time < ?"));
        assertTrue(source.contains("end_time > ?"));
        assertTrue(source.contains("SELECT id"));
        assertTrue(source.contains("LIMIT 1"));
    }

    @Test
    void workorderUsesCanonicalColumnsAndSelfScope() throws IOException {
        String source = read("src/main/java/com/dehui/property/modules/workorder/controller/WorkOrderController.java");
        assertTrue(source.contains("w.order_type AS orderType"));
        assertTrue(source.contains("w.location"));
        assertTrue(source.contains("w.description"));
        assertTrue(source.contains("submitter_mobile_user_id = ?"));
    }

    @Test
    void visitorUsesCanonicalFieldsAndSelfScope() throws IOException {
        String source = read("src/main/java/com/dehui/property/modules/visitor/controller/VisitorController.java");
        assertTrue(source.contains("visit_company AS visitCompany"));
        assertTrue(source.contains("visit_reason AS visitReason"));
        assertTrue(source.contains("applicant_mobile_user_id = ?"));
        assertTrue(source.contains("'EXPIRED'"));
    }

    @Test
    void mobileCustomFieldsAreValidatedAndPersisted() throws IOException {
        String source = read("src/main/java/com/dehui/property/modules/mobile/service/MobileCustomFieldValueService.java");
        assertTrue(source.contains("validator.validate"));
        assertTrue(source.contains("uniqueValueExists"));
        assertTrue(source.contains("replaceValues"));
    }

    @Test
    void mobileSchemaRegistryUsesCanonicalWorkorderAndVisitorKeys() throws IOException {
        String source = read("src/main/java/com/dehui/property/modules/customfield/application/ModuleFieldRegistry.java");
        assertTrue(source.contains("\"reporterPhone\", \"联系电话\""));
        assertTrue(source.contains("\"reporter_phone\""));
        assertTrue(source.contains("\"visitCompany\""));
        assertTrue(source.contains("\"visitReason\""));
    }

    private String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
