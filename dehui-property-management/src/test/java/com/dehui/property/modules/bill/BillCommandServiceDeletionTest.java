package com.dehui.property.modules.bill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.bill.application.BillCommandService;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class BillCommandServiceDeletionTest {

    private static final long BILL_ID = 9L;
    private static final long VERSION = 3L;
    private static final long OPERATOR_ID = 7_001L;
    private static final String BILL_NUMBER = "BIL-MANUAL-001";
    private static final String REASON = "Remove isolated test draft";

    @Test
    void eligibleManualDraftIsLogicallyDeletedByAuthenticatedOperatorAndAudited() {
        JdbcTemplate jdbcTemplate = eligibleDraftJdbc();
        when(jdbcTemplate.update(contains("UPDATE bill"), any(Object[].class))).thenReturn(1);

        deleteBillRecord(new BillCommandService(jdbcTemplate), VERSION, REASON, BILL_NUMBER, OPERATOR_ID);

        ArgumentCaptor<Object[]> updateArgs = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(argThat(sql -> {
            String normalized = normalize(sql);
            return normalized.contains("UPDATE BILL")
                    && normalized.contains("DELETED = 1")
                    && normalized.contains("STATUS = 'DELETED'")
                    && normalized.contains("VERSION = VERSION + 1");
        }), updateArgs.capture());
        assertTrue(Arrays.asList(updateArgs.getValue()).contains(OPERATOR_ID));
        assertTrue(Arrays.asList(updateArgs.getValue()).contains(BILL_ID));
        assertTrue(Arrays.asList(updateArgs.getValue()).contains(VERSION));

        ArgumentCaptor<Object[]> auditArgs = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(contains("INSERT INTO bill_audit_log"), auditArgs.capture());
        List<Object> auditPayload = Arrays.asList(auditArgs.getValue());
        assertTrue(auditPayload.contains("DELETE_RECORD"));
        assertTrue(auditPayload.contains(REASON));
        assertTrue(auditPayload.contains(OPERATOR_ID));
        verify(jdbcTemplate).queryForList(argThat(sql -> {
            String normalized = normalize(sql);
            return normalized.contains("FROM BILL_LINE_ITEM") && normalized.contains("FOR UPDATE");
        }), eq(BILL_ID));
        verify(jdbcTemplate).queryForList(argThat(sql -> {
            String normalized = normalize(sql);
            return normalized.contains("FROM CUSTOM_FIELD_VALUE") && normalized.contains("FOR UPDATE");
        }), eq(BILL_ID), eq(BILL_ID));
    }

    @Test
    void approvedRecordWithoutAnyFinancialHistoryCanBeDeletedForControlledTestDataCleanup() {
        Map<String, Object> approved = eligibleDraft();
        approved.put("workflow_status", "APPROVED");
        approved.put("audit_status", "APPROVED");
        JdbcTemplate jdbcTemplate = eligibleJdbc(approved);
        when(jdbcTemplate.update(contains("UPDATE bill"), any(Object[].class))).thenReturn(1);

        deleteBillRecord(new BillCommandService(jdbcTemplate), VERSION, REASON, BILL_NUMBER, OPERATOR_ID, true);

        verify(jdbcTemplate).update(argThat(sql -> normalize(sql).contains("STATUS = 'DELETED'")), any(Object[].class));
    }

    @Test
    void approvedRecordRequiresTheAdditionalApprovedDeletionCapability() {
        Map<String, Object> approved = eligibleDraft();
        approved.put("workflow_status", "APPROVED");
        approved.put("audit_status", "APPROVED");
        JdbcTemplate jdbcTemplate = eligibleJdbc(approved);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> deleteBillRecord(
                        new BillCommandService(jdbcTemplate), VERSION, REASON, BILL_NUMBER, OPERATOR_ID, false
                )
        );

        assertEquals(403, exception.getCode());
        verifyNoWrites(jdbcTemplate);
    }

    @Test
    void billNumberConfirmationMustMatchLockedRecord() {
        JdbcTemplate jdbcTemplate = eligibleDraftJdbc();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> deleteBillRecord(
                        new BillCommandService(jdbcTemplate), VERSION, REASON, "WRONG-BILL-NUMBER", OPERATOR_ID
                )
        );

        assertEquals(400, exception.getCode());
        verifyNoWrites(jdbcTemplate);
    }

    @Test
    void staleVersionReturnsConflictWithoutWriting() {
        JdbcTemplate jdbcTemplate = eligibleDraftJdbc();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> deleteBillRecord(
                        new BillCommandService(jdbcTemplate), VERSION + 1, REASON, BILL_NUMBER, OPERATOR_ID
                )
        );

        assertEquals(409, exception.getCode());
        verifyNoWrites(jdbcTemplate);
    }

    @Test
    void paidAmountRejectsDeletionWithoutWriting() {
        Map<String, Object> paidDraft = eligibleDraft();
        paidDraft.put("pay_status", "PAID");
        paidDraft.put("paid_amount", new BigDecimal("1.00"));
        JdbcTemplate jdbcTemplate = jdbcFor(paidDraft);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> deleteBillRecord(
                        new BillCommandService(jdbcTemplate), VERSION, REASON, BILL_NUMBER, OPERATOR_ID
                )
        );

        assertEquals(409, exception.getCode());
        verifyNoWrites(jdbcTemplate);
    }

    @Test
    void negativePaidAmountAlsoRejectsDeletionAsAnInconsistentFinancialState() {
        Map<String, Object> inconsistentDraft = eligibleDraft();
        inconsistentDraft.put("paid_amount", new BigDecimal("-1.00"));
        JdbcTemplate jdbcTemplate = jdbcFor(inconsistentDraft);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> deleteBillRecord(
                        new BillCommandService(jdbcTemplate), VERSION, REASON, BILL_NUMBER, OPERATOR_ID
                )
        );

        assertEquals(409, exception.getCode());
        verifyNoWrites(jdbcTemplate);
    }

    @Test
    void anyPaymentHistoryRejectsDeletionEvenWhenHistoryWasLogicallyDeleted() {
        JdbcTemplate jdbcTemplate = eligibleDraftJdbc();
        when(jdbcTemplate.queryForObject(contains("bill_payment_record"), eq(Integer.class), eq(BILL_ID)))
                .thenReturn(1);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> deleteBillRecord(
                        new BillCommandService(jdbcTemplate), VERSION, REASON, BILL_NUMBER, OPERATOR_ID
                )
        );

        assertEquals(409, exception.getCode());
        verify(jdbcTemplate).queryForObject(argThat(sql ->
                        normalize(sql).contains("BILL_PAYMENT_RECORD")
                                && !normalize(sql).contains("DELETED = 0")),
                eq(Integer.class), eq(BILL_ID));
        verifyNoWrites(jdbcTemplate);
    }

    @Test
    void anyInvoiceHistoryRejectsDeletionEvenWhenInvoiceWasVoided() {
        JdbcTemplate jdbcTemplate = eligibleDraftJdbc();
        when(jdbcTemplate.queryForObject(contains("bill_invoice"), eq(Integer.class), eq(BILL_ID)))
                .thenReturn(1);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> deleteBillRecord(
                        new BillCommandService(jdbcTemplate), VERSION, REASON, BILL_NUMBER, OPERATOR_ID
                )
        );

        assertEquals(409, exception.getCode());
        verify(jdbcTemplate).queryForObject(argThat(sql ->
                        normalize(sql).contains("BILL_INVOICE")
                                && !normalize(sql).contains("DELETED = 0")),
                eq(Integer.class), eq(BILL_ID));
        verifyNoWrites(jdbcTemplate);
    }

    @Test
    void billInvoiceStateRejectsDeletionEvenWhenInvoiceHistoryRowIsMissing() {
        Map<String, Object> inconsistentDraft = eligibleDraft();
        inconsistentDraft.put("invoice_status", "INVOICED");
        JdbcTemplate jdbcTemplate = eligibleJdbc(inconsistentDraft);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> deleteBillRecord(
                        new BillCommandService(jdbcTemplate), VERSION, REASON, BILL_NUMBER, OPERATOR_ID
                )
        );

        assertEquals(409, exception.getCode());
        verifyNoWrites(jdbcTemplate);
    }

    @Test
    void billAttachmentHistoryRejectsDeletionWithoutWriting() {
        JdbcTemplate jdbcTemplate = eligibleDraftJdbc();
        when(jdbcTemplate.queryForObject(contains("file_upload"), eq(Integer.class), eq(BILL_ID)))
                .thenReturn(1);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> deleteBillRecord(
                        new BillCommandService(jdbcTemplate), VERSION, REASON, BILL_NUMBER, OPERATOR_ID
                )
        );

        assertEquals(409, exception.getCode());
        verify(jdbcTemplate).queryForObject(argThat(sql -> {
            String normalized = normalize(sql);
            return normalized.contains("FILE_UPLOAD")
                    && normalized.contains("WORKSPACE_KEY = 'BILLS'")
                    && normalized.contains("BIZ_ID = ?")
                    && !normalized.contains("DELETED = 0");
        }), eq(Integer.class), eq(BILL_ID));
        verifyNoWrites(jdbcTemplate);
    }

    @Test
    void chargeBasisLinkRejectsDeletionWithoutWriting() {
        JdbcTemplate jdbcTemplate = eligibleDraftJdbc();
        when(jdbcTemplate.queryForObject(contains("charge_basis_id"), eq(Integer.class), eq(BILL_ID)))
                .thenReturn(1);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> deleteBillRecord(
                        new BillCommandService(jdbcTemplate), VERSION, REASON, BILL_NUMBER, OPERATOR_ID
                )
        );

        assertEquals(409, exception.getCode());
        verifyNoWrites(jdbcTemplate);
    }

    @Test
    void adjustmentChainRejectsDeletionWithoutWriting() {
        JdbcTemplate jdbcTemplate = eligibleDraftJdbc();
        when(jdbcTemplate.queryForObject(contains("adjustment_of_bill_id"), eq(Integer.class), eq(BILL_ID)))
                .thenReturn(1);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> deleteBillRecord(
                        new BillCommandService(jdbcTemplate), VERSION, REASON, BILL_NUMBER, OPERATOR_ID
                )
        );

        assertEquals(409, exception.getCode());
        verifyNoWrites(jdbcTemplate);
    }

    @Test
    void adjustmentLineReferenceRejectsDeletionWithoutWriting() {
        JdbcTemplate jdbcTemplate = eligibleDraftJdbc();
        when(jdbcTemplate.queryForObject(argThat(sql -> {
                    String normalized = normalize(sql);
                    return normalized.contains("FROM BILL_LINE_ITEM")
                            && normalized.contains("SOURCE_TYPE = 'BILL'")
                            && normalized.contains("SOURCE_ID = ?");
                }), eq(Integer.class), eq(BILL_ID)))
                .thenReturn(1);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> deleteBillRecord(
                        new BillCommandService(jdbcTemplate), VERSION, REASON, BILL_NUMBER, OPERATOR_ID
                )
        );

        assertEquals(409, exception.getCode());
        verifyNoWrites(jdbcTemplate);
    }

    @Test
    void adjustmentSourceReferenceRejectsDeletionWithoutWriting() {
        JdbcTemplate jdbcTemplate = eligibleDraftJdbc();
        when(jdbcTemplate.queryForObject(argThat(sql -> {
                    String normalized = normalize(sql);
                    return normalized.contains("FROM BILL")
                            && normalized.contains("SOURCE_TYPE = 'ADJUSTMENT'")
                            && normalized.contains("SOURCE_ID = ?");
                }), eq(Integer.class), eq(BILL_ID)))
                .thenReturn(1);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> deleteBillRecord(
                        new BillCommandService(jdbcTemplate), VERSION, REASON, BILL_NUMBER, OPERATOR_ID
                )
        );

        assertEquals(409, exception.getCode());
        verifyNoWrites(jdbcTemplate);
    }

    @Test
    void upstreamBusinessReferenceRejectsDeletionWithoutWriting() {
        JdbcTemplate jdbcTemplate = eligibleDraftJdbc();
        when(jdbcTemplate.queryForObject(contains("asset_charge_basis"), eq(Integer.class), eq(BILL_ID)))
                .thenReturn(1);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> deleteBillRecord(
                        new BillCommandService(jdbcTemplate), VERSION, REASON, BILL_NUMBER, OPERATOR_ID
                )
        );

        assertEquals(409, exception.getCode());
        verifyNoWrites(jdbcTemplate);
    }

    @Test
    void businessGeneratedSourceRejectsDeletionEvenWhenNoReverseReferenceExists() {
        Map<String, Object> generatedDraft = eligibleDraft();
        generatedDraft.put("source_type", "WORK_ORDER");
        generatedDraft.put("source_id", 77L);
        JdbcTemplate jdbcTemplate = eligibleJdbc(generatedDraft);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> deleteBillRecord(
                        new BillCommandService(jdbcTemplate), VERSION, REASON, BILL_NUMBER, OPERATOR_ID
                )
        );

        assertEquals(409, exception.getCode());
        verifyNoWrites(jdbcTemplate);
    }

    @Test
    void manualParentWithSourceIdRejectsDeletionAsNonIsolatedData() {
        Map<String, Object> linkedManualDraft = eligibleDraft();
        linkedManualDraft.put("source_id", 77L);
        JdbcTemplate jdbcTemplate = eligibleJdbc(linkedManualDraft);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> deleteBillRecord(
                        new BillCommandService(jdbcTemplate), VERSION, REASON, BILL_NUMBER, OPERATOR_ID
                )
        );

        assertEquals(409, exception.getCode());
        verifyNoWrites(jdbcTemplate);
    }

    @Test
    void manualBillWithBusinessSourcedLineRejectsDeletion() {
        JdbcTemplate jdbcTemplate = eligibleDraftJdbc();
        when(jdbcTemplate.queryForObject(argThat(sql -> {
                    String normalized = normalize(sql);
                    return normalized.contains("FROM BILL_LINE_ITEM")
                            && normalized.contains("COALESCE(SOURCE_TYPE, 'MANUAL')")
                            && normalized.contains("SOURCE_ID IS NOT NULL");
                }), eq(Integer.class), eq(BILL_ID)))
                .thenReturn(1);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> deleteBillRecord(
                        new BillCommandService(jdbcTemplate), VERSION, REASON, BILL_NUMBER, OPERATOR_ID
                )
        );

        assertEquals(409, exception.getCode());
        verifyNoWrites(jdbcTemplate);
    }

    @Test
    void deletionPreflightRejectsMissingVersionBeforeRunningDependencyQueries() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(argThat(sql ->
                        normalize(sql).equals("SELECT * FROM BILL WHERE ID = ? AND DELETED = 0")),
                eq(BILL_ID))).thenReturn(List.of(eligibleDraft()));

        Map<String, Object> result = new BillCommandService(jdbcTemplate)
                .deletionEligibility(BILL_ID, null, false);

        assertEquals(false, result.get("allowed"));
        assertEquals("MISSING_VERSION", result.get("blockedCode"));
        verify(jdbcTemplate, never()).queryForObject(any(String.class), eq(Integer.class), eq(BILL_ID));
    }

    private JdbcTemplate eligibleDraftJdbc() {
        return eligibleJdbc(eligibleDraft());
    }

    private JdbcTemplate eligibleJdbc(Map<String, Object> bill) {
        JdbcTemplate jdbcTemplate = jdbcFor(bill);
        when(jdbcTemplate.queryForObject(contains("bill_payment_record"), eq(Integer.class), eq(BILL_ID)))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject(contains("bill_invoice"), eq(Integer.class), eq(BILL_ID)))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject(contains("charge_basis_id"), eq(Integer.class), eq(BILL_ID)))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject(contains("adjustment_of_bill_id"), eq(Integer.class), eq(BILL_ID)))
                .thenReturn(0);
        return jdbcTemplate;
    }

    private JdbcTemplate jdbcFor(Map<String, Object> bill) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(contains("FOR UPDATE"), eq(BILL_ID)))
                .thenReturn(List.of(bill));
        return jdbcTemplate;
    }

    private Map<String, Object> eligibleDraft() {
        Map<String, Object> bill = new LinkedHashMap<>();
        bill.put("id", BILL_ID);
        bill.put("code", BILL_NUMBER);
        bill.put("source_type", "MANUAL");
        bill.put("source_id", null);
        bill.put("workflow_status", "DRAFT");
        bill.put("audit_status", "PENDING");
        bill.put("pay_status", "UNPAID");
        bill.put("invoice_status", "NOT_INVOICED");
        bill.put("status", "ACTIVE");
        bill.put("paid_amount", BigDecimal.ZERO);
        bill.put("adjustment_of_bill_id", null);
        bill.put("version", VERSION);
        return bill;
    }

    private void verifyNoWrites(JdbcTemplate jdbcTemplate) {
        verify(jdbcTemplate, never()).update(any(String.class), any(Object[].class));
    }

    private void deleteBillRecord(
            BillCommandService service,
            Long expectedVersion,
            String reason,
            String confirmBillNumber,
            Long operatorId) {
        deleteBillRecord(service, expectedVersion, reason, confirmBillNumber, operatorId, false);
    }

    private void deleteBillRecord(
            BillCommandService service,
            Long expectedVersion,
            String reason,
            String confirmBillNumber,
            Long operatorId,
            boolean approvedDeletionAllowed) {
        Method method;
        try {
            method = BillCommandService.class.getMethod(
                    "deleteBillRecord",
                    Long.class,
                    Long.class,
                    String.class,
                    String.class,
                    Long.class,
                    boolean.class
            );
        } catch (NoSuchMethodException exception) {
            throw new AssertionError("BillCommandService.deleteBillRecord contract is missing", exception);
        }

        try {
            method.invoke(
                    service,
                    BILL_ID,
                    expectedVersion,
                    reason,
                    confirmBillNumber,
                    operatorId,
                    approvedDeletionAllowed
            );
        } catch (IllegalAccessException exception) {
            throw new AssertionError("BillCommandService.deleteBillRecord must be public", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            if (cause instanceof Error error) throw error;
            throw new AssertionError("Unexpected checked exception from bill deletion", cause);
        }
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toUpperCase();
    }
}
