package com.dehui.property.modules.bill.application;

import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcMaps;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillCommandService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public BillCommandService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    public BigDecimal requirePositiveAmount(Map<String, Object> body) {
        try {
            BigDecimal amount = JdbcMaps.decimal(body, null, "amount");
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(400, "账单金额必须大于0");
            }
            return amount;
        } catch (NumberFormatException exception) {
            throw new BusinessException(400, "账单金额格式不正确");
        }
    }

    @Transactional
    public Long createManualDraft(Map<String, Object> body, Long operatorId) {
        requireOperator(operatorId);
        Long tenantId = JdbcMaps.requiredLong(body, "租户不能为空", "tenantId", "tenant_id");
        Long contractId = JdbcMaps.longVal(body, "contractId", "contract_id");
        validateContractTenant(tenantId, contractId);
        BigDecimal calculated = decimal(body == null ? null : body.get("calculatedAmount"));
        if (calculated.compareTo(BigDecimal.ZERO) == 0) {
            calculated = requirePositiveAmount(body);
        }
        BigDecimal adjustment = decimal(body == null ? null : body.get("adjustmentAmount"));
        String adjustmentReason = text(body, "adjustmentReason");
        requireAdjustmentReason(adjustment, adjustmentReason);
        BigDecimal finalAmount = money(calculated.add(adjustment));
        if (finalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "账单最终金额必须大于0");
        }
        String billingPeriod = billingPeriod(body);
        String billCode = text(body, "billNumber", "code");
        if (billCode == null) billCode = JdbcMaps.code("BIL");
        LocalDate dueDate = date(body, "dueDate", LocalDate.now());
        LocalDate periodStart = date(body, "periodStart", periodStart(billingPeriod));
        LocalDate periodEnd = date(body, "periodEnd", periodEnd(billingPeriod));

        jdbcTemplate.update(
                """
                INSERT INTO bill (
                    code, tenant_id, contract_id, source_type, source_id, bill_type, title,
                    amount, calculated_total, adjustment_total, final_amount, paid_amount,
                    period_start, period_end, billing_period, bill_date, due_date,
                    audit_status, workflow_status, pay_status, invoice_status, version,
                    status, created_by, updated_by, remark
                )
                VALUES (?, ?, ?, 'MANUAL', NULL, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, CURRENT_DATE, ?,
                        'PENDING', 'DRAFT', 'UNPAID', 'NOT_INVOICED', 0,
                        'ACTIVE', ?, ?, ?)
                """,
                billCode,
                tenantId,
                contractId,
                textOr(body, "OTHER", "billType"),
                text(body, "title"),
                finalAmount,
                calculated,
                adjustment,
                finalAmount,
                periodStart,
                periodEnd,
                billingPeriod,
                dueDate,
                operatorId,
                operatorId,
                text(body, "remark")
        );
        Long billId = jdbcTemplate.queryForObject(
                "SELECT id FROM bill WHERE code = ? AND deleted = 0 ORDER BY id DESC LIMIT 1",
                Long.class,
                billCode
        );
        insertManualLine(
                billId,
                textOr(body, "OTHER", "billType"),
                textOr(body, textOr(body, "人工费用", "title"), "description"),
                calculated,
                adjustment,
                adjustmentReason,
                operatorId
        );
        audit(billId, "GENERATE_BILL", null, billSnapshot(billId), text(body, "remark"), operatorId);
        return billId;
    }

    @Transactional
    public void updateDraft(Long billId, Map<String, Object> body, Long operatorId) {
        requireOperator(operatorId);
        Map<String, Object> before = lockBill(billId);
        requireDraft(before);
        Long tenantId = JdbcMaps.requiredLong(body, "租户不能为空", "tenantId", "tenant_id");
        Long contractId = JdbcMaps.longVal(body, "contractId", "contract_id");
        if (!tenantId.equals(longValue(before.get("tenant_id")))) {
            Integer basisCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM bill_line_item WHERE bill_id = ? AND charge_basis_id IS NOT NULL AND deleted = 0 AND status = 'ACTIVE'",
                    Integer.class,
                    billId
            );
            if (basisCount != null && basisCount > 0) {
                throw new BusinessException(409, "已包含计费依据的草稿不能变更租户");
            }
        }
        validateContractTenant(tenantId, contractId);
        Long expectedVersion = longValue(body == null ? null : body.get("version"));
        if (expectedVersion != null && expectedVersion.longValue() != longValue(before.get("version")).longValue()) {
            throw new BusinessException(409, "账单已被其他人修改，请刷新后重试");
        }
        int updated = jdbcTemplate.update(
                """
                UPDATE bill
                SET code = COALESCE(?, code),
                    tenant_id = ?,
                    contract_id = ?,
                    bill_type = COALESCE(?, bill_type),
                    title = ?,
                    period_start = ?,
                    period_end = ?,
                    billing_period = ?,
                    due_date = ?,
                    remark = ?,
                    version = version + 1,
                    updated_by = ?
                WHERE id = ? AND deleted = 0 AND workflow_status = 'DRAFT' AND version = ?
                """,
                text(body, "billNumber", "code"),
                tenantId,
                contractId,
                text(body, "billType"),
                text(body, "title"),
                date(body, "periodStart", localDate(before.get("period_start"))),
                date(body, "periodEnd", localDate(before.get("period_end"))),
                textOr(body, string(before.get("billing_period")), "billingPeriod"),
                date(body, "dueDate", localDate(before.get("due_date"))),
                text(body, "remark"),
                operatorId,
                billId,
                before.get("version")
        );
        if (updated != 1) throw new BusinessException(409, "账单状态已变化，请刷新后重试");

        if (body != null && body.containsKey("calculatedAmount")) {
            replaceLegacyManualLine(billId, body, operatorId);
        }
        recalculateTotals(billId, operatorId);
        audit(billId, "EDIT_DRAFT", before, billSnapshot(billId), text(body, "remark"), operatorId);
    }

    @Transactional
    public void adjustLine(Long billId, Long lineId, BigDecimal adjustmentAmount, String reason, Long operatorId) {
        requireOperator(operatorId);
        Map<String, Object> bill = lockBill(billId);
        requireDraft(bill);
        BigDecimal adjustment = money(adjustmentAmount);
        requireAdjustmentReason(adjustment, reason);
        List<Map<String, Object>> lines = jdbcTemplate.queryForList(
                """
                SELECT id, calculated_amount AS calculatedAmount, adjustment_amount AS adjustmentAmount
                FROM bill_line_item
                WHERE id = ? AND bill_id = ? AND deleted = 0 AND status = 'ACTIVE'
                FOR UPDATE
                """,
                lineId,
                billId
        );
        if (lines.isEmpty()) throw new BusinessException(404, "账单明细不存在");
        BigDecimal calculated = decimal(lines.getFirst().get("calculatedAmount"));
        BigDecimal finalAmount = money(calculated.add(adjustment));
        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(400, "明细最终金额不能小于0");
        }
        jdbcTemplate.update(
                """
                UPDATE bill_line_item
                SET adjustment_amount = ?, final_amount = ?, adjustment_reason = ?,
                    updated_by = ?
                WHERE id = ? AND bill_id = ? AND deleted = 0 AND status = 'ACTIVE'
                """,
                adjustment,
                finalAmount,
                reason.trim(),
                operatorId,
                lineId,
                billId
        );
        recalculateTotals(billId, operatorId);
        audit(billId, "ADJUST_AMOUNT", lines.getFirst(), Map.of(
                "lineId", lineId,
                "adjustmentAmount", adjustment,
                "finalAmount", finalAmount
        ), reason, operatorId);
    }

    @Transactional
    public void submitReview(Long billId, Long expectedVersion, Long operatorId) {
        transition(
                billId,
                expectedVersion,
                "DRAFT",
                "PENDING_REVIEW",
                "PENDING",
                "SUBMIT_REVIEW",
                null,
                operatorId
        );
    }

    @Transactional
    public void approve(Long billId, Long expectedVersion, String opinion, Long operatorId) {
        requireOperator(operatorId);
        Map<String, Object> before = lockBill(billId);
        requireVersion(before, expectedVersion);
        if (!"PENDING_REVIEW".equals(string(before.get("workflow_status")))) {
            throw new BusinessException(409, "只有待审核账单可以审核通过");
        }
        recalculateTotals(billId, operatorId);
        int updated = jdbcTemplate.update(
                """
                UPDATE bill
                SET workflow_status = 'APPROVED', audit_status = 'APPROVED',
                    reviewed_by = ?, reviewed_at = CURRENT_TIMESTAMP, review_opinion = ?,
                    version = version + 1, updated_by = ?
                WHERE id = ? AND deleted = 0
                  AND workflow_status = 'PENDING_REVIEW' AND version = ?
                """,
                operatorId,
                opinion,
                operatorId,
                billId,
                before.get("version")
        );
        if (updated != 1) throw new BusinessException(409, "账单已被其他人处理，请刷新后重试");
        audit(billId, "APPROVE", before, billSnapshot(billId), opinion, operatorId);
    }

    @Transactional
    public void reject(Long billId, Long expectedVersion, String opinion, Long operatorId) {
        if (opinion == null || opinion.isBlank()) {
            throw new BusinessException(400, "驳回原因不能为空");
        }
        transition(
                billId,
                expectedVersion,
                "PENDING_REVIEW",
                "DRAFT",
                "REJECTED",
                "REJECT",
                opinion,
                operatorId
        );
    }

    @Transactional
    public void voidBill(Long billId, Long expectedVersion, String reason, Long operatorId) {
        requireOperator(operatorId);
        if (reason == null || reason.isBlank()) throw new BusinessException(400, "作废原因不能为空");
        Map<String, Object> before = lockBill(billId);
        requireVersion(before, expectedVersion);
        String workflow = string(before.get("workflow_status"));
        if (!List.of("DRAFT", "PENDING_REVIEW", "APPROVED").contains(workflow)) {
            throw new BusinessException(409, "当前账单状态不能作废");
        }
        if (decimal(before.get("paid_amount")).compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(409, "已有收款的账单不能直接作废");
        }
        int updated = jdbcTemplate.update(
                """
                UPDATE bill
                SET workflow_status = 'VOID', status = 'VOID', pay_status = 'CANCELLED',
                    voided_by = ?, voided_at = CURRENT_TIMESTAMP, void_reason = ?,
                    version = version + 1, updated_by = ?
                WHERE id = ? AND deleted = 0 AND version = ?
                """,
                operatorId,
                reason.trim(),
                operatorId,
                billId,
                before.get("version")
        );
        if (updated != 1) throw new BusinessException(409, "账单已被其他人处理，请刷新后重试");
        audit(billId, "VOID", before, billSnapshot(billId), reason, operatorId);
    }

    @Transactional
    public void deleteBillRecord(
            Long billId,
            Long expectedVersion,
            String reason,
            String confirmBillNumber,
            Long operatorId) {
        deleteBillRecord(billId, expectedVersion, reason, confirmBillNumber, operatorId, false);
    }

    @Transactional
    public void deleteBillRecord(
            Long billId,
            Long expectedVersion,
            String reason,
            String confirmBillNumber,
            Long operatorId,
            boolean approvedRecordDeletionAllowed) {
        requireOperator(operatorId);
        if (expectedVersion == null) {
            throw new BusinessException(400, "账单版本不能为空，请刷新后重试");
        }
        String deletionReason = reason == null ? "" : reason.trim();
        if (deletionReason.isEmpty()) {
            throw new BusinessException(400, "删除原因不能为空");
        }
        if (deletionReason.length() > 500) {
            throw new BusinessException(400, "删除原因不能超过500个字符");
        }

        Map<String, Object> before = lockBill(billId);
        String billNumber = string(before.get("code"));
        if (confirmBillNumber == null || !billNumber.equals(confirmBillNumber.trim())) {
            throw new BusinessException(400, "确认账单编号与当前账单不一致");
        }
        requireVersion(before, expectedVersion);
        requireDeletableRecordState(before, approvedRecordDeletionAllowed);
        requireNoDeletionDependencies(billId);

        List<Map<String, Object>> lineSnapshot = jdbcTemplate.queryForList(
                """
                SELECT *
                FROM bill_line_item
                WHERE bill_id = ?
                ORDER BY id
                FOR UPDATE
                """,
                billId
        );
        List<Map<String, Object>> customValueSnapshot = jdbcTemplate.queryForList(
                """
                SELECT *
                FROM custom_field_value
                WHERE (workspace_key = 'bills' AND entity_id = ?)
                   OR (module = 'bills' AND record_id = ?)
                ORDER BY id
                FOR UPDATE
                """,
                billId,
                billId
        );
        Map<String, Object> deletionSnapshot = new LinkedHashMap<>();
        deletionSnapshot.put("bill", new LinkedHashMap<>(before));
        deletionSnapshot.put("lines", lineSnapshot);
        deletionSnapshot.put("customValues", customValueSnapshot);

        int updated = jdbcTemplate.update(
                """
                UPDATE bill
                SET deleted = 1,
                    status = 'DELETED',
                    version = version + 1,
                    updated_by = ?
                WHERE id = ? AND deleted = 0 AND version = ?
                """,
                operatorId,
                billId,
                expectedVersion
        );
        if (updated != 1) {
            throw new BusinessException(409, "账单已被其他人处理，请刷新后重试");
        }
        jdbcTemplate.update(
                """
                UPDATE bill_line_item
                SET deleted = 1, status = 'REMOVED', updated_by = ?
                WHERE bill_id = ? AND deleted = 0
                """,
                operatorId,
                billId
        );
        jdbcTemplate.update(
                """
                UPDATE custom_field_value
                SET deleted = 1, version = version + 1, updated_by = ?
                WHERE deleted = 0
                  AND ((workspace_key = 'bills' AND entity_id = ?)
                       OR (module = 'bills' AND record_id = ?))
                """,
                operatorId,
                billId,
                billId
        );
        audit(
                billId,
                "DELETE_RECORD",
                deletionSnapshot,
                Map.of(
                        "id", billId,
                        "code", billNumber,
                        "deleted", true,
                        "status", "DELETED",
                        "version", expectedVersion + 1
                ),
                deletionReason,
                operatorId
        );
    }

    public Map<String, Object> deletionEligibility(
            Long billId,
            Long expectedVersion,
            boolean approvedRecordDeletionAllowed) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM bill WHERE id = ? AND deleted = 0",
                billId
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "账单不存在");
        }
        Map<String, Object> bill = rows.getFirst();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("billId", billId);
        result.put("billNumber", string(bill.get("code")));
        result.put("version", longValue(bill.get("version")));
        if (expectedVersion == null) {
            result.put("allowed", false);
            result.put("blockedCode", "MISSING_VERSION");
            result.put("blockedReason", "账单版本不能为空，请刷新后重试");
            return result;
        }
        if (expectedVersion != null && !expectedVersion.equals(longValue(bill.get("version")))) {
            result.put("allowed", false);
            result.put("blockedCode", "STALE_VERSION");
            result.put("blockedReason", "账单已被其他人修改，请刷新后重试");
            return result;
        }
        try {
            requireDeletableRecordState(bill, approvedRecordDeletionAllowed);
            requireNoDeletionDependencies(billId);
            result.put("allowed", true);
            result.put("blockedCode", "");
            result.put("blockedReason", "");
        } catch (BusinessException exception) {
            result.put("allowed", false);
            result.put(
                    "blockedCode",
                    exception.getCode() == 403 ? "APPROVED_PERMISSION_REQUIRED" : "BUSINESS_RULE_BLOCKED"
            );
            result.put("blockedReason", exception.getMessage());
        }
        return result;
    }

    @Transactional
    public Long createAdjustmentDraft(
            Long referenceBillId,
            BigDecimal adjustmentAmount,
            String reason,
            LocalDate dueDate,
            Long operatorId) {
        requireOperator(operatorId);
        BigDecimal signedAmount = money(adjustmentAmount);
        if (signedAmount.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException(400, "补差或红冲金额不能为0");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(400, "补差或红冲原因不能为空");
        }
        Map<String, Object> reference = lockBill(referenceBillId);
        if (!"APPROVED".equals(string(reference.get("workflow_status")))) {
            throw new BusinessException(409, "只有已审核锁定账单才能创建后续调整单");
        }
        String code = JdbcMaps.code(signedAmount.signum() > 0 ? "SUP" : "REV");
        String billingPeriod = string(reference.get("billing_period"));
        LocalDate periodStart = localDate(reference.get("period_start"));
        LocalDate periodEnd = localDate(reference.get("period_end"));
        LocalDate targetDueDate = dueDate == null ? LocalDate.now() : dueDate;
        String title = (signedAmount.signum() > 0 ? "补差单-" : "红冲单-") + string(reference.get("code"));
        jdbcTemplate.update(
                """
                INSERT INTO bill (
                    code, tenant_id, contract_id, source_type, source_id,
                    bill_type, title, amount, calculated_total, adjustment_total, final_amount,
                    paid_amount, period_start, period_end, billing_period, bill_date, due_date,
                    audit_status, workflow_status, pay_status, invoice_status, version,
                    adjustment_of_bill_id, status, created_by, updated_by, remark
                )
                VALUES (?, ?, ?, 'ADJUSTMENT', ?, 'ADJUSTMENT', ?, ?, 0, ?, ?, 0,
                        ?, ?, ?, CURRENT_DATE, ?, 'PENDING', 'DRAFT', 'UNPAID',
                        'NOT_INVOICED', 0, ?, 'ACTIVE', ?, ?, ?)
                """,
                code,
                reference.get("tenant_id"),
                reference.get("contract_id"),
                referenceBillId,
                title,
                signedAmount,
                signedAmount,
                signedAmount,
                periodStart,
                periodEnd,
                billingPeriod,
                targetDueDate,
                referenceBillId,
                operatorId,
                operatorId,
                reason.trim()
        );
        Long billId = jdbcTemplate.queryForObject(
                "SELECT id FROM bill WHERE code = ? AND deleted = 0",
                Long.class,
                code
        );
        jdbcTemplate.update(
                """
                INSERT INTO bill_line_item (
                    bill_id, charge_item_type, source_type, source_id, source_snapshot,
                    description, calculated_amount, adjustment_amount, final_amount,
                    adjustment_reason, sort_order, status, created_by, updated_by
                )
                VALUES (?, 'ADJUSTMENT', 'BILL', ?, ?, ?, 0, ?, ?, ?, 1, 'ACTIVE', ?, ?)
                """,
                billId,
                referenceBillId,
                json(Map.of(
                        "referenceBillId", referenceBillId,
                        "referenceBillNumber", string(reference.get("code")),
                        "referenceFinalAmount", decimal(reference.get("final_amount"))
                )),
                title,
                signedAmount,
                signedAmount,
                reason.trim(),
                operatorId,
                operatorId
        );
        audit(billId, signedAmount.signum() > 0 ? "CREATE_SUPPLEMENT" : "CREATE_REVERSAL",
                Map.of("referenceBillId", referenceBillId),
                billSnapshot(billId),
                reason,
                operatorId);
        return billId;
    }

    public List<Map<String, Object>> lines(Long billId) {
        return jdbcTemplate.queryForList(
                """
                SELECT id,
                       charge_basis_id AS chargeBasisId,
                       charge_item_type AS chargeItemType,
                       source_type AS sourceType,
                       source_id AS sourceId,
                       source_snapshot AS sourceSnapshot,
                       description,
                       quantity_snapshot AS quantitySnapshot,
                       unit,
                       unit_price_snapshot AS unitPriceSnapshot,
                       calculated_amount AS calculatedAmount,
                       adjustment_amount AS adjustmentAmount,
                       final_amount AS finalAmount,
                       adjustment_reason AS adjustmentReason,
                       sort_order AS sortOrder
                FROM bill_line_item
                WHERE bill_id = ? AND deleted = 0 AND status = 'ACTIVE'
                ORDER BY sort_order, id
                """,
                billId
        );
    }

    public List<Map<String, Object>> auditLogs(Long billId) {
        return jdbcTemplate.queryForList(
                """
                SELECT log_record.id,
                       log_record.action,
                       log_record.before_snapshot AS beforeSnapshot,
                       log_record.after_snapshot AS afterSnapshot,
                       log_record.reason,
                       log_record.operator_id AS operatorId,
                       COALESCE(user_record.real_name, user_record.username) AS operatorName,
                       log_record.occurred_at AS occurredAt
                FROM bill_audit_log log_record
                LEFT JOIN sys_user user_record
                       ON user_record.id = log_record.operator_id AND user_record.deleted = 0
                WHERE log_record.bill_id = ?
                ORDER BY log_record.occurred_at DESC, log_record.id DESC
                """,
                billId
        );
    }

    @Transactional
    public void confirmOfflinePayment(Long billId, Long operatorId) {
        if (operatorId == null) {
            throw BusinessException.unauthorized("登录状态已失效");
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT tenant_id, amount, final_amount, audit_status, workflow_status, pay_status, status
                FROM bill
                WHERE id = ? AND deleted = 0
                FOR UPDATE
                """,
                billId
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "账单不存在");
        }

        Map<String, Object> bill = rows.getFirst();
        String payStatus = String.valueOf(bill.get("pay_status"));
        String auditStatus = String.valueOf(bill.get("audit_status"));
        String workflowStatus = String.valueOf(bill.get("workflow_status"));
        String recordStatus = String.valueOf(bill.get("status"));

        if ("PAID".equals(payStatus)) {
            throw new BusinessException(409, "账单已收款，不能重复收款");
        }
        if (!"APPROVED".equals(auditStatus) || !"APPROVED".equals(workflowStatus)) {
            throw new BusinessException(409, "只有审核通过的账单才能收款");
        }
        if (!"UNPAID".equals(payStatus)) {
            throw new BusinessException(409, "当前支付状态不允许收款");
        }
        if (!"ACTIVE".equals(recordStatus)) {
            throw new BusinessException(409, "当前账单状态不允许收款");
        }

        BigDecimal amount = decimalValue(bill.get("final_amount"));
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(409, "零金额账单不能收款");
        }

        Integer existingPayments = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bill_payment_record WHERE bill_id = ? AND deleted = 0 AND status = 'PAID'",
                Integer.class,
                billId
        );
        if (existingPayments != null && existingPayments > 0) {
            throw new BusinessException(409, "账单已存在有效收款记录");
        }

        int updated = jdbcTemplate.update(
                """
                UPDATE bill
                SET paid_amount = final_amount, pay_status = 'PAID',
                    version = version + 1, updated_by = ?
                WHERE id = ? AND deleted = 0
                  AND audit_status = 'APPROVED'
                  AND workflow_status = 'APPROVED'
                  AND pay_status = 'UNPAID'
                  AND status = 'ACTIVE'
                """,
                operatorId,
                billId
        );
        if (updated != 1) {
            throw new BusinessException(409, "账单状态已变化，请刷新后重试");
        }

        jdbcTemplate.update(
                """
                INSERT INTO bill_payment_record
                    (code, bill_id, tenant_id, pay_channel, amount, paid_at, status, created_by, updated_by)
                VALUES (?, ?, ?, 'OFFLINE', ?, ?, 'PAID', ?, ?)
                """,
                nextPaymentCode(),
                billId,
                bill.get("tenant_id"),
                amount,
                JdbcMaps.now(),
                operatorId,
                operatorId
        );
        audit(billId, "CONFIRM_PAYMENT", bill, billSnapshot(billId), "线下确认收款", operatorId);
    }

    protected String nextPaymentCode() {
        return JdbcMaps.code("PAY");
    }

    private BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        if (value == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    private void transition(
            Long billId,
            Long expectedVersion,
            String from,
            String to,
            String auditStatus,
            String action,
            String reason,
            Long operatorId
    ) {
        requireOperator(operatorId);
        Map<String, Object> before = lockBill(billId);
        requireVersion(before, expectedVersion);
        if (!from.equals(string(before.get("workflow_status")))) {
            throw new BusinessException(409, "账单状态已变化，请刷新后重试");
        }
        int updated = jdbcTemplate.update(
                """
                UPDATE bill
                SET workflow_status = ?, audit_status = ?, review_opinion = ?,
                    version = version + 1, updated_by = ?
                WHERE id = ? AND deleted = 0 AND workflow_status = ? AND version = ?
                """,
                to,
                auditStatus,
                reason,
                operatorId,
                billId,
                from,
                before.get("version")
        );
        if (updated != 1) throw new BusinessException(409, "账单已被其他人处理，请刷新后重试");
        audit(billId, action, before, billSnapshot(billId), reason, operatorId);
    }

    private void replaceLegacyManualLine(Long billId, Map<String, Object> body, Long operatorId) {
        List<Map<String, Object>> lines = jdbcTemplate.queryForList(
                """
                SELECT id
                FROM bill_line_item
                WHERE bill_id = ? AND source_type IN ('MANUAL', 'LEGACY')
                  AND charge_basis_id IS NULL AND deleted = 0 AND status = 'ACTIVE'
                ORDER BY id
                """,
                billId
        );
        if (lines.size() != 1) {
            throw new BusinessException(409, "多明细账单请逐行调整，不能覆盖系统计算总额");
        }
        BigDecimal calculated = decimal(body.get("calculatedAmount"));
        if (calculated.compareTo(BigDecimal.ZERO) == 0) calculated = requirePositiveAmount(body);
        BigDecimal adjustment = decimal(body.get("adjustmentAmount"));
        String reason = text(body, "adjustmentReason");
        requireAdjustmentReason(adjustment, reason);
        BigDecimal finalAmount = money(calculated.add(adjustment));
        if (finalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "账单最终金额必须大于0");
        }
        jdbcTemplate.update(
                """
                UPDATE bill_line_item
                SET calculated_amount = ?, adjustment_amount = ?, final_amount = ?,
                    adjustment_reason = ?, description = COALESCE(?, description),
                    updated_by = ?
                WHERE id = ?
                """,
                calculated,
                adjustment,
                finalAmount,
                reason,
                text(body, "description", "title"),
                operatorId,
                lines.getFirst().get("id")
        );
    }

    private void insertManualLine(
            Long billId,
            String chargeItemType,
            String description,
            BigDecimal calculated,
            BigDecimal adjustment,
            String reason,
            Long operatorId
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO bill_line_item (
                    bill_id, charge_basis_id, charge_item_type, source_type, source_id,
                    source_snapshot, description, quantity_snapshot, unit, unit_price_snapshot,
                    calculated_amount, adjustment_amount, final_amount, adjustment_reason,
                    sort_order, status, created_by, updated_by
                )
                VALUES (?, NULL, ?, 'MANUAL', NULL, NULL, ?, 1, '项', ?,
                        ?, ?, ?, ?, 0, 'ACTIVE', ?, ?)
                """,
                billId,
                chargeItemType,
                description,
                calculated,
                calculated,
                adjustment,
                money(calculated.add(adjustment)),
                reason,
                operatorId,
                operatorId
        );
    }

    private void recalculateTotals(Long billId, Long operatorId) {
        Map<String, Object> totals = jdbcTemplate.queryForMap(
                """
                SELECT COALESCE(SUM(calculated_amount), 0) AS calculatedTotal,
                       COALESCE(SUM(adjustment_amount), 0) AS adjustmentTotal,
                       COALESCE(SUM(final_amount), 0) AS finalAmount
                FROM bill_line_item
                WHERE bill_id = ? AND deleted = 0 AND status = 'ACTIVE'
                """,
                billId
        );
        BigDecimal finalAmount = money(decimal(totals.get("finalAmount")));
        String billType = jdbcTemplate.queryForObject(
                "SELECT bill_type FROM bill WHERE id = ? AND deleted = 0",
                String.class,
                billId
        );
        if (finalAmount.compareTo(BigDecimal.ZERO) == 0
                || (finalAmount.compareTo(BigDecimal.ZERO) < 0 && !"ADJUSTMENT".equals(billType))) {
            throw new BusinessException(409, "普通账单最终金额必须大于0，红冲单金额可以为负数");
        }
        jdbcTemplate.update(
                """
                UPDATE bill
                SET calculated_total = ?, adjustment_total = ?, final_amount = ?,
                    amount = ?, updated_by = ?
                WHERE id = ? AND deleted = 0
                """,
                money(decimal(totals.get("calculatedTotal"))),
                money(decimal(totals.get("adjustmentTotal"))),
                finalAmount,
                finalAmount,
                operatorId,
                billId
        );
    }

    private Map<String, Object> lockBill(Long billId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM bill WHERE id = ? AND deleted = 0 FOR UPDATE",
                billId
        );
        if (rows.isEmpty()) throw new BusinessException(404, "账单不存在");
        return new LinkedHashMap<>(rows.getFirst());
    }

    private Map<String, Object> billSnapshot(Long billId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, code, tenant_id AS tenantId, billing_period AS billingPeriod,
                       calculated_total AS calculatedTotal, adjustment_total AS adjustmentTotal,
                       final_amount AS finalAmount, paid_amount AS paidAmount,
                       workflow_status AS workflowStatus, audit_status AS auditStatus,
                       pay_status AS payStatus, invoice_status AS invoiceStatus, version
                FROM bill WHERE id = ? AND deleted = 0
                """,
                billId
        );
        return rows.isEmpty() ? Map.of() : new LinkedHashMap<>(rows.getFirst());
    }

    private void audit(
            Long billId,
            String action,
            Object before,
            Object after,
            String reason,
            Long operatorId
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO bill_audit_log (
                    bill_id, action, before_snapshot, after_snapshot, reason, operator_id
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                billId,
                action,
                json(before),
                json(after),
                reason,
                operatorId
        );
    }

    private void requireDraft(Map<String, Object> bill) {
        if (!"DRAFT".equals(string(bill.get("workflow_status")))) {
            throw new BusinessException(409, "只有草稿账单可以编辑");
        }
        if (decimal(bill.get("paid_amount")).compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(409, "已有收款的账单不能编辑");
        }
    }

    private void requireDeletableRecordState(
            Map<String, Object> bill,
            boolean approvedRecordDeletionAllowed) {
        String workflowStatus = string(bill.get("workflow_status"));
        String auditStatus = string(bill.get("audit_status"));
        String payStatus = string(bill.get("pay_status"));
        String status = string(bill.get("status"));
        String sourceType = string(bill.get("source_type"));

        boolean draft = "DRAFT".equals(workflowStatus)
                && List.of("PENDING", "REJECTED").contains(auditStatus);
        boolean approved = "APPROVED".equals(workflowStatus)
                && "APPROVED".equals(auditStatus);
        if (approved && !approvedRecordDeletionAllowed) {
            throw BusinessException.forbidden("删除已审核账单需要额外的敏感财务权限");
        }
        if (!draft && !approved) {
            throw new BusinessException(409, "只有草稿或无财务历史的已审核账单可以删除；待审核、作废和取消账单请按原流程处理");
        }
        if (!List.of("ACTIVE", "ENABLED").contains(status)) {
            throw new BusinessException(409, "当前账单状态不能删除");
        }
        if (!List.of("UNPAID", "OVERDUE").contains(payStatus)
                || decimal(bill.get("paid_amount")).compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException(409, "已收款或部分收款账单不能删除");
        }
        if (!"NOT_INVOICED".equals(string(bill.get("invoice_status")))) {
            throw new BusinessException(409, "账单开票状态不允许删除；请先核对发票记录");
        }
        if (!"MANUAL".equals(sourceType)
                || bill.get("source_id") != null
                || bill.get("adjustment_of_bill_id") != null) {
            throw new BusinessException(409, "只有手工创建的账单可以删除；业务来源账单请从来源模块撤回");
        }
    }

    private void requireNoDeletionDependencies(Long billId) {
        if (historyCount("SELECT COUNT(*) FROM bill_payment_record WHERE bill_id = ?", billId) > 0) {
            throw new BusinessException(409, "账单存在收款历史，不能删除");
        }
        if (historyCount("SELECT COUNT(*) FROM bill_invoice WHERE bill_id = ?", billId) > 0) {
            throw new BusinessException(409, "账单存在发票历史，不能删除");
        }
        if (historyCount(
                "SELECT COUNT(*) FROM file_upload WHERE workspace_key = 'bills' AND biz_id = ?",
                billId
        ) > 0) {
            throw new BusinessException(409, "账单存在自定义字段附件历史，不能删除");
        }
        if (historyCount(
                "SELECT COUNT(*) FROM bill_line_item WHERE bill_id = ? AND charge_basis_id IS NOT NULL",
                billId
        ) > 0) {
            throw new BusinessException(409, "账单包含计费依据，不能直接删除");
        }
        if (historyCount(
                """
                SELECT COUNT(*) FROM bill_line_item
                WHERE bill_id = ?
                  AND (source_id IS NOT NULL OR COALESCE(source_type, 'MANUAL') <> 'MANUAL')
                """,
                billId
        ) > 0) {
            throw new BusinessException(409, "账单明细仍携带业务来源，不能删除");
        }
        if (historyCount("SELECT COUNT(*) FROM bill WHERE adjustment_of_bill_id = ?", billId) > 0) {
            throw new BusinessException(409, "账单已有关联补差或红冲记录，不能删除");
        }
        if (historyCount(
                "SELECT COUNT(*) FROM bill_line_item WHERE source_type = 'BILL' AND source_id = ?",
                billId
        ) > 0 || historyCount(
                "SELECT COUNT(*) FROM bill WHERE source_type = 'ADJUSTMENT' AND source_id = ?",
                billId
        ) > 0) {
            throw new BusinessException(409, "账单已被补差或红冲来源链引用，不能删除");
        }
        if (historyCount("SELECT COUNT(*) FROM asset_charge_basis WHERE bill_id = ?", billId) > 0
                || historyCount("SELECT COUNT(*) FROM energy_reading WHERE bill_id = ?", billId) > 0
                || historyCount("SELECT COUNT(*) FROM meeting_bill WHERE bill_id = ?", billId) > 0
                || historyCount("SELECT COUNT(*) FROM energy_bill_operation_log WHERE bill_id = ?", billId) > 0) {
            throw new BusinessException(409, "账单仍被来源业务引用，不能删除");
        }
    }

    private int historyCount(String sql, Long billId) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, billId);
        return count == null ? 0 : count;
    }

    private void requireVersion(Map<String, Object> bill, Long expectedVersion) {
        if (expectedVersion != null && !expectedVersion.equals(longValue(bill.get("version")))) {
            throw new BusinessException(409, "账单已被其他人修改，请刷新后重试");
        }
    }

    private void validateContractTenant(Long tenantId, Long contractId) {
        if (contractId == null) return;
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM contract WHERE id = ? AND tenant_id = ? AND deleted = 0",
                Integer.class,
                contractId,
                tenantId
        );
        if (count == null || count == 0) throw new BusinessException(400, "合同不属于当前租户");
    }

    private void requireOperator(Long operatorId) {
        if (operatorId == null) throw BusinessException.unauthorized("登录状态已失效");
    }

    private void requireAdjustmentReason(BigDecimal adjustment, String reason) {
        if (adjustment.compareTo(BigDecimal.ZERO) != 0 && (reason == null || reason.isBlank())) {
            throw new BusinessException(400, "调整金额不为0时必须填写调整原因");
        }
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) return money(decimal);
        if (value instanceof Number number) return money(new BigDecimal(number.toString()));
        if (value == null || String.valueOf(value).isBlank()) return BigDecimal.ZERO.setScale(2);
        try {
            return money(new BigDecimal(String.valueOf(value)));
        } catch (NumberFormatException exception) {
            throw new BusinessException(400, "金额格式不正确");
        }
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String billingPeriod(Map<String, Object> body) {
        String explicit = text(body, "billingPeriod");
        if (explicit != null) return explicit;
        LocalDate periodStart = date(body, "periodStart", null);
        return periodStart == null
                ? java.time.YearMonth.now().toString()
                : java.time.YearMonth.from(periodStart).toString();
    }

    private LocalDate periodStart(String billingPeriod) {
        try {
            return java.time.YearMonth.parse(billingPeriod).atDay(1);
        } catch (Exception exception) {
            return LocalDate.now();
        }
    }

    private LocalDate periodEnd(String billingPeriod) {
        try {
            return java.time.YearMonth.parse(billingPeriod).atEndOfMonth();
        } catch (Exception exception) {
            return LocalDate.now();
        }
    }

    private LocalDate date(Map<String, Object> body, String key, LocalDate fallback) {
        Object value = body == null ? null : body.get(key);
        if (value == null || String.valueOf(value).isBlank()) return fallback;
        try {
            return LocalDate.parse(String.valueOf(value));
        } catch (Exception exception) {
            throw new BusinessException(400, key + " 日期格式必须为 YYYY-MM-DD");
        }
    }

    private LocalDate localDate(Object value) {
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        if (value instanceof LocalDate localDate) return localDate;
        if (value == null) return null;
        return LocalDate.parse(String.valueOf(value));
    }

    private String text(Map<String, Object> body, String... keys) {
        if (body == null) return null;
        for (String key : keys) {
            Object value = body.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value).trim();
        }
        return null;
    }

    private String textOr(Map<String, Object> body, String fallback, String... keys) {
        String value = text(body, keys);
        return value == null ? fallback : value;
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null || String.valueOf(value).isBlank()) return null;
        return Long.valueOf(String.valueOf(value));
    }

    private String json(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "审计快照序列化失败");
        }
    }
}
