package com.dehui.property.modules.bill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.bill.application.BillCommandService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/** Destructive opt-in coverage restricted to the local dehui_property_dev database. */
@EnabledIfEnvironmentVariable(named = "BILL_DB_INTEGRATION", matches = "true")
class BillDeleteRecordMySqlIntegrationTest {

    private static final long CREATOR_ID = 71_001L;
    private static final long DELETE_OPERATOR_ID = 72_001L;
    private static final long INITIAL_VERSION = 2L;
    private static final String DELETE_REASON = "LOCAL BILL RECORD DELETION INTEGRATION TEST";

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private BillCommandService service;
    private Long tenantId;

    private final Map<Long, String> billRecords = new LinkedHashMap<>();
    private final Map<Long, String> definitionRecords = new LinkedHashMap<>();
    private final List<Long> lineIds = new ArrayList<>();
    private final List<Long> customValueIds = new ArrayList<>();
    private final List<Long> paymentIds = new ArrayList<>();
    private final List<Long> fileIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        String jdbcUrl = requiredEnvironment("MYSQL_URL");
        requireLocalDevelopmentJdbcUrl(jdbcUrl);
        dataSource = new DriverManagerDataSource(
                jdbcUrl,
                requiredEnvironment("MYSQL_USERNAME"),
                requiredEnvironment("MYSQL_PASSWORD")
        );
        assertDevelopmentDatabase(dataSource);
        jdbcTemplate = new JdbcTemplate(dataSource);
        service = new BillCommandService(jdbcTemplate);
        tenantId = jdbcTemplate.queryForObject(
                "SELECT id FROM tenant WHERE deleted = 0 AND status IN ('ACTIVE', 'ENABLED') ORDER BY id LIMIT 1",
                Long.class
        );
        assertNotNull(tenantId, "dehui_property_dev needs one active tenant for isolated bill tests");
    }

    @AfterEach
    void tearDown() {
        if (jdbcTemplate == null) return;

        for (Long billId : billRecords.keySet()) {
            List<Long> auditIds = jdbcTemplate.queryForList(
                    "SELECT id FROM bill_audit_log WHERE bill_id = ?",
                    Long.class,
                    billId
            );
            for (Long auditId : auditIds) {
                jdbcTemplate.update(
                        "DELETE FROM bill_audit_log WHERE id = ? AND bill_id = ?",
                        auditId,
                        billId
                );
            }
        }
        for (Long paymentId : paymentIds) {
            jdbcTemplate.update("DELETE FROM bill_payment_record WHERE id = ?", paymentId);
        }
        for (Long fileId : fileIds) {
            jdbcTemplate.update("DELETE FROM file_upload WHERE id = ?", fileId);
        }
        for (Long customValueId : customValueIds) {
            jdbcTemplate.update("DELETE FROM custom_field_value WHERE id = ?", customValueId);
        }
        for (Long lineId : lineIds) {
            jdbcTemplate.update("DELETE FROM bill_line_item WHERE id = ?", lineId);
        }
        for (Map.Entry<Long, String> bill : billRecords.entrySet()) {
            jdbcTemplate.update(
                    "DELETE FROM bill WHERE id = ? AND code = ?",
                    bill.getKey(),
                    bill.getValue()
            );
        }
        for (Map.Entry<Long, String> definition : definitionRecords.entrySet()) {
            jdbcTemplate.update(
                    "DELETE FROM custom_field_definition WHERE id = ? AND code = ?",
                    definition.getKey(),
                    definition.getValue()
            );
        }
    }

    @Test
    void manualDraftDeletionLogicallyDeletesBillLineAndCustomValueAndKeepsOperatorAudit() throws Exception {
        assertEligibleRecordDeletion("DRAFT", "PENDING");
    }

    @Test
    void approvedBillWithoutFinancialHistoryUsesTheSameAuditedLogicalDeletion() throws Exception {
        assertEligibleRecordDeletion("APPROVED", "APPROVED");
    }

    @Test
    void anyPaymentHistoryReturnsConflictAndLeavesTheWholeRecordUnchanged() {
        TestRecord record = createRecord("APPROVED", "APPROVED");
        Long paymentId = createDeletedPaymentHistory(record.billId());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> inTransaction(() -> service.deleteBillRecord(
                        record.billId(),
                        INITIAL_VERSION,
                        DELETE_REASON,
                        record.billNumber(),
                        DELETE_OPERATOR_ID,
                        true
                ))
        );

        assertEquals(409, exception.getCode());
        assertEquals(0, intValue("SELECT deleted FROM bill WHERE id = ?", record.billId()));
        assertEquals("ACTIVE", textValue("SELECT status FROM bill WHERE id = ?", record.billId()));
        assertEquals(INITIAL_VERSION, longValue("SELECT version FROM bill WHERE id = ?", record.billId()));
        assertEquals(CREATOR_ID, longValue("SELECT updated_by FROM bill WHERE id = ?", record.billId()));
        assertEquals(0, intValue("SELECT deleted FROM bill_line_item WHERE id = ?", record.lineId()));
        assertEquals("ACTIVE", textValue("SELECT status FROM bill_line_item WHERE id = ?", record.lineId()));
        assertEquals(CREATOR_ID, longValue("SELECT updated_by FROM bill_line_item WHERE id = ?", record.lineId()));
        assertEquals(0, intValue("SELECT deleted FROM custom_field_value WHERE id = ?", record.customValueId()));
        assertEquals(0L, longValue("SELECT version FROM custom_field_value WHERE id = ?", record.customValueId()));
        assertEquals(CREATOR_ID, longValue("SELECT updated_by FROM custom_field_value WHERE id = ?", record.customValueId()));
        assertEquals(1, intValue("SELECT deleted FROM bill_payment_record WHERE id = ?", paymentId));
        assertEquals(0, intValue(
                "SELECT COUNT(*) FROM bill_audit_log WHERE bill_id = ? AND action = 'DELETE_RECORD'",
                record.billId()
        ));
    }

    @Test
    void anyBoundCustomFieldAttachmentReturnsConflictAndLeavesTheRecordVisible() {
        TestRecord record = createRecord("DRAFT", "PENDING");
        Long fileId = createBoundAttachmentHistory(record.billId());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> inTransaction(() -> service.deleteBillRecord(
                        record.billId(),
                        INITIAL_VERSION,
                        DELETE_REASON,
                        record.billNumber(),
                        DELETE_OPERATOR_ID,
                        false
                ))
        );

        assertEquals(409, exception.getCode());
        assertEquals(0, intValue("SELECT deleted FROM bill WHERE id = ?", record.billId()));
        assertEquals(0, intValue("SELECT deleted FROM file_upload WHERE id = ?", fileId));
        assertEquals(0, intValue(
                "SELECT COUNT(*) FROM bill_audit_log WHERE bill_id = ? AND action = 'DELETE_RECORD'",
                record.billId()
        ));
    }

    private void assertEligibleRecordDeletion(String workflowStatus, String auditStatus) throws Exception {
        TestRecord record = createRecord(workflowStatus, auditStatus);

        inTransaction(() -> service.deleteBillRecord(
                record.billId(),
                INITIAL_VERSION,
                DELETE_REASON,
                record.billNumber(),
                DELETE_OPERATOR_ID,
                "APPROVED".equals(workflowStatus)
        ));

        assertEquals(1, intValue("SELECT deleted FROM bill WHERE id = ?", record.billId()));
        assertEquals("DELETED", textValue("SELECT status FROM bill WHERE id = ?", record.billId()));
        assertEquals(INITIAL_VERSION + 1, longValue("SELECT version FROM bill WHERE id = ?", record.billId()));
        assertEquals(DELETE_OPERATOR_ID, longValue("SELECT updated_by FROM bill WHERE id = ?", record.billId()));

        assertEquals(1, intValue("SELECT deleted FROM bill_line_item WHERE id = ?", record.lineId()));
        assertEquals("REMOVED", textValue("SELECT status FROM bill_line_item WHERE id = ?", record.lineId()));
        assertEquals(DELETE_OPERATOR_ID, longValue("SELECT updated_by FROM bill_line_item WHERE id = ?", record.lineId()));

        assertEquals(1, intValue("SELECT deleted FROM custom_field_value WHERE id = ?", record.customValueId()));
        assertEquals(1L, longValue("SELECT version FROM custom_field_value WHERE id = ?", record.customValueId()));
        assertEquals(DELETE_OPERATOR_ID, longValue("SELECT updated_by FROM custom_field_value WHERE id = ?", record.customValueId()));

        List<Map<String, Object>> auditRows = jdbcTemplate.queryForList(
                """
                SELECT action, reason, operator_id AS operatorId, before_snapshot AS beforeSnapshot,
                       after_snapshot AS afterSnapshot
                FROM bill_audit_log
                WHERE bill_id = ? AND action = 'DELETE_RECORD'
                """,
                record.billId()
        );
        assertEquals(1, auditRows.size());
        assertEquals(DELETE_REASON, auditRows.getFirst().get("reason"));
        assertEquals(DELETE_OPERATOR_ID, ((Number) auditRows.getFirst().get("operatorId")).longValue());
        assertNotNull(auditRows.getFirst().get("beforeSnapshot"));
        assertNotNull(auditRows.getFirst().get("afterSnapshot"));
        JsonNode beforeSnapshot = new ObjectMapper().readTree(
                String.valueOf(auditRows.getFirst().get("beforeSnapshot"))
        );
        assertEquals(record.lineId().longValue(), beforeSnapshot.path("lines").get(0).path("id").asLong());
        assertEquals(
                CREATOR_ID,
                beforeSnapshot.path("lines").get(0).path("updated_by").asLong(),
                "the snapshot must preserve the pre-delete line operator"
        );
        assertTrue(beforeSnapshot.path("lines").get(0).has("source_snapshot"));
        assertEquals(
                record.customValueId().longValue(),
                beforeSnapshot.path("customValues").get(0).path("id").asLong()
        );
        assertEquals(
                "integration",
                beforeSnapshot.path("customValues").get(0).path("value_text").asText()
        );
    }

    private TestRecord createRecord(String workflowStatus, String auditStatus) {
        String suffix = uniqueSuffix();
        String billNumber = "CODX-BDEL-" + suffix;
        jdbcTemplate.update(
                """
                INSERT INTO bill (
                    code, tenant_id, contract_id, source_type, source_id, bill_type, title,
                    amount, calculated_total, adjustment_total, final_amount, paid_amount,
                    period_start, period_end, billing_period, bill_date, due_date,
                    audit_status, workflow_status, pay_status, invoice_status, version,
                    status, created_by, updated_by, deleted, remark
                )
                VALUES (?, ?, NULL, 'MANUAL', NULL, 'OTHER', ?,
                        88.00, 88.00, 0, 88.00, 0,
                        CURRENT_DATE, CURRENT_DATE, DATE_FORMAT(CURRENT_DATE, '%Y-%m'), CURRENT_DATE, CURRENT_DATE,
                        ?, ?, 'UNPAID', 'NOT_INVOICED', ?,
                        'ACTIVE', ?, ?, 0, 'LOCAL BILL DELETE RECORD INTEGRATION TEST')
                """,
                billNumber,
                tenantId,
                "Local deletion " + suffix,
                auditStatus,
                workflowStatus,
                INITIAL_VERSION,
                CREATOR_ID,
                CREATOR_ID
        );
        Long billId = jdbcTemplate.queryForObject(
                "SELECT id FROM bill WHERE code = ? AND deleted = 0",
                Long.class,
                billNumber
        );
        assertNotNull(billId);
        billRecords.put(billId, billNumber);

        jdbcTemplate.update(
                """
                INSERT INTO bill_line_item (
                    bill_id, charge_basis_id, charge_item_type, source_type, source_id,
                    source_snapshot, description, quantity_snapshot, unit, unit_price_snapshot,
                    calculated_amount, adjustment_amount, final_amount, adjustment_reason,
                    sort_order, status, created_by, updated_by, deleted
                )
                VALUES (?, NULL, 'OTHER', 'MANUAL', NULL,
                        NULL, ?, 1, 'ITEM', 88.00,
                        88.00, 0, 88.00, NULL,
                        0, 'ACTIVE', ?, ?, 0)
                """,
                billId,
                "Local deletion line " + suffix,
                CREATOR_ID,
                CREATOR_ID
        );
        Long lineId = jdbcTemplate.queryForObject(
                "SELECT id FROM bill_line_item WHERE bill_id = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                billId
        );
        assertNotNull(lineId);
        lineIds.add(lineId);

        String definitionCode = "CODX-BDEL-FIELD-" + suffix;
        String fieldKey = "codx_bill_delete_" + suffix.toLowerCase();
        jdbcTemplate.update(
                """
                INSERT INTO custom_field_definition (
                    code, module, workspace_key, entity_type, field_key, label, field_type,
                    status, deleted, created_by, updated_by
                )
                VALUES (?, 'bills', 'bills', 'bill', ?, ?, 'TEXT',
                        'ENABLED', 0, ?, ?)
                """,
                definitionCode,
                fieldKey,
                "Local delete field " + suffix,
                CREATOR_ID,
                CREATOR_ID
        );
        Long definitionId = jdbcTemplate.queryForObject(
                "SELECT id FROM custom_field_definition WHERE code = ?",
                Long.class,
                definitionCode
        );
        assertNotNull(definitionId);
        definitionRecords.put(definitionId, definitionCode);

        jdbcTemplate.update(
                """
                INSERT INTO custom_field_value (
                    field_id, module, workspace_key, entity_type, record_id, entity_id, field_key,
                    value_json, value_text, version, deleted, created_by, updated_by
                )
                VALUES (?, 'bills', 'bills', 'bill', ?, ?, ?,
                        ?, ?, 0, 0, ?, ?)
                """,
                definitionId,
                billId,
                billId,
                fieldKey,
                "\"integration\"",
                "integration",
                CREATOR_ID,
                CREATOR_ID
        );
        Long customValueId = jdbcTemplate.queryForObject(
                """
                SELECT id FROM custom_field_value
                WHERE field_id = ? AND entity_id = ? AND field_key = ?
                """,
                Long.class,
                definitionId,
                billId,
                fieldKey
        );
        assertNotNull(customValueId);
        customValueIds.add(customValueId);
        return new TestRecord(billId, billNumber, lineId, customValueId);
    }

    private Long createDeletedPaymentHistory(Long billId) {
        String paymentCode = "CODX-BDEL-PAY-" + uniqueSuffix();
        jdbcTemplate.update(
                """
                INSERT INTO bill_payment_record (
                    code, bill_id, tenant_id, pay_channel, amount, paid_at, status,
                    created_by, updated_by, deleted, remark
                )
                VALUES (?, ?, ?, 'TEST', 1.00, CURRENT_TIMESTAMP, 'VOID',
                        ?, ?, 1, 'LOCAL BILL DELETE RECORD INTEGRATION TEST')
                """,
                paymentCode,
                billId,
                tenantId,
                CREATOR_ID,
                CREATOR_ID
        );
        Long paymentId = jdbcTemplate.queryForObject(
                "SELECT id FROM bill_payment_record WHERE code = ? AND deleted = 1",
                Long.class,
                paymentCode
        );
        assertNotNull(paymentId);
        paymentIds.add(paymentId);
        return paymentId;
    }

    private Long createBoundAttachmentHistory(Long billId) {
        String fileCode = "CODX-BDEL-FILE-" + uniqueSuffix();
        jdbcTemplate.update(
                """
                INSERT INTO file_upload (
                    code, biz_type, biz_id, workspace_key, original_name, stored_name,
                    storage_path, access_scope, owner_sys_user_id, mime_type, file_size,
                    status, created_by, updated_by, deleted, remark
                )
                VALUES (?, 'CUSTOM_FIELD', ?, 'bills', 'blocked.txt', ?,
                        'local-integration/blocked.txt', 'INTERNAL', ?, 'text/plain', 7,
                        'ENABLED', ?, ?, 0, 'LOCAL BILL DELETE RECORD INTEGRATION TEST')
                """,
                fileCode,
                billId,
                fileCode + ".txt",
                CREATOR_ID,
                CREATOR_ID,
                CREATOR_ID
        );
        Long fileId = jdbcTemplate.queryForObject(
                "SELECT id FROM file_upload WHERE code = ? AND deleted = 0",
                Long.class,
                fileCode
        );
        assertNotNull(fileId);
        fileIds.add(fileId);
        return fileId;
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(new DataSourceTransactionManager(dataSource))
                .executeWithoutResult(status -> action.run());
    }

    private int intValue(String sql, Object argument) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, argument);
        assertNotNull(value);
        return value;
    }

    private long longValue(String sql, Object argument) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, argument);
        assertNotNull(value);
        return value;
    }

    private String textValue(String sql, Object argument) {
        String value = jdbcTemplate.queryForObject(sql, String.class, argument);
        assertNotNull(value);
        return value;
    }

    private static String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }

    private static void requireLocalDevelopmentJdbcUrl(String jdbcUrl) {
        if (!jdbcUrl.matches(
                "(?i)^jdbc:mysql://(?:localhost|127\\.0\\.0\\.1)(?::\\d+)?/dehui_property_dev(?:\\?.*)?$"
        )) {
            throw new IllegalStateException(
                    "Bill deletion integration tests only allow local dehui_property_dev"
            );
        }
    }

    private static void assertDevelopmentDatabase(DriverManagerDataSource source) throws Exception {
        try (Connection connection = source.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT DATABASE()")) {
            resultSet.next();
            assertEquals("dehui_property_dev", resultSet.getString(1));
        }
    }

    private record TestRecord(Long billId, String billNumber, Long lineId, Long customValueId) {}
}
