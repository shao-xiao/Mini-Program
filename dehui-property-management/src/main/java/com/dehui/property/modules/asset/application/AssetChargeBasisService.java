package com.dehui.property.modules.asset.application;

import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcMaps;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 资产域与财务域之间的唯一收费边界。
 *
 * <p>资产模块只确认带快照的计费依据；只有本服务的 finance 方法会写统一 bill 表。
 */
@Service
public class AssetChargeBasisService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AssetChargeBasisService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Long confirmEnergyBasis(Long readingId, Long operatorId) {
        Map<String, Object> reading = requireOne(
                """
                SELECT er.id,
                       er.period_month AS billingPeriod,
                       er.reading_date AS readingDate,
                       er.previous_reading AS previousReading,
                       er.current_reading AS currentReading,
                       er.usage_amount AS quantity,
                       er.unit_price AS unitPrice,
                       er.settlement_amount AS amount,
                       er.meter_id AS meterId,
                       m.meter_no AS meterNo,
                       m.meter_type AS meterType,
                       m.unit,
                       m.room_id AS roomId
                FROM energy_reading er
                JOIN energy_meter m ON m.id = er.meter_id AND m.deleted = 0
                WHERE er.id = ? AND er.deleted = 0
                FOR UPDATE
                """,
                readingId,
                "抄表记录不存在"
        );
        Map<String, Object> binding = activeMeterBinding(
                longValue(reading.get("meterId")),
                localDate(reading.get("readingDate"))
        );
        Long tenantId = longValue(binding.get("tenantId"));
        String subjectType = stringValue(binding.get("subjectType"));
        BigDecimal amount = decimal(reading.get("amount"));
        if ("TENANT".equals(subjectType) && tenantId == null) {
            throw new BusinessException(409, "租户计量主体缺少租户关系，不能确认费用依据");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(409, "结算金额不能小于0");
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("meterNo", reading.get("meterNo"));
        snapshot.put("meterType", reading.get("meterType"));
        snapshot.put("subjectType", subjectType);
        snapshot.put("bindingId", binding.get("id"));
        snapshot.put("previousReading", reading.get("previousReading"));
        snapshot.put("currentReading", reading.get("currentReading"));
        snapshot.put("multiplier", reading.get("multiplier"));
        snapshot.put("roomId", binding.get("roomId"));

        Long basisId = upsertConfirmedBasis(
                "ENERGY",
                readingId,
                tenantId,
                null,
                longValue(binding.get("roomId")),
                longValue(reading.get("meterId")),
                null,
                null,
                stringValue(reading.get("billingPeriod")),
                decimal(reading.get("quantity")),
                stringValue(reading.get("unit")),
                decimal(reading.get("unitPrice")),
                amount,
                snapshot,
                operatorId,
                "能耗抄表费用依据"
        );
        jdbcTemplate.update(
                """
                UPDATE energy_reading
                SET charge_basis_id = ?,
                    bill_status = CASE WHEN bill_id IS NULL THEN 'BASIS_CONFIRMED' ELSE bill_status END,
                    updated_by = ?
                WHERE id = ? AND deleted = 0
                """,
                basisId,
                operatorId,
                readingId
        );
        return basisId;
    }

    @Transactional
    public int syncParkingBases(String billingPeriod, Long operatorId) {
        YearMonth month = parseMonth(billingPeriod);
        LocalDate firstDay = month.atDay(1);
        LocalDate lastDay = month.atEndOfMonth();
        List<Map<String, Object>> assignments = jdbcTemplate.queryForList(
                """
                SELECT pa.id,
                       pa.tenant_id AS tenantId,
                       pa.parking_space_id AS parkingSpaceId,
                       pa.car_plate_no AS plateNo,
                       pa.vehicle_type AS vehicleType,
                       pa.start_date AS effectiveFrom,
                       pa.end_date AS effectiveTo,
                       COALESCE(pa.unit_price_snapshot, pa.monthly_fee, ps.monthly_rate) AS unitPrice,
                       ps.space_no AS spaceNo
                FROM parking_assignment pa
                JOIN parking_space ps ON ps.id = pa.parking_space_id AND ps.deleted = 0
                WHERE pa.deleted = 0
                  AND pa.status IN ('ACTIVE', 'ENABLED', 'RELEASED')
                  AND pa.start_date <= ?
                  AND (pa.end_date IS NULL OR pa.end_date >= ?)
                ORDER BY pa.id
                FOR UPDATE
                """,
                Date.valueOf(lastDay),
                Date.valueOf(firstDay)
        );
        int confirmed = 0;
        for (Map<String, Object> assignment : assignments) {
            BigDecimal unitPrice = decimal(assignment.get("unitPrice"));
            if (unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("spaceNo", assignment.get("spaceNo"));
            snapshot.put("plateNo", assignment.get("plateNo"));
            snapshot.put("vehicleType", assignment.get("vehicleType"));
            snapshot.put("effectiveFrom", assignment.get("effectiveFrom"));
            snapshot.put("effectiveTo", assignment.get("effectiveTo"));
            snapshot.put("billingRule", "账期内存在有效绑定按整月计费");
            upsertConfirmedBasis(
                    "PARKING",
                    longValue(assignment.get("id")),
                    longValue(assignment.get("tenantId")),
                    null,
                    null,
                    null,
                    longValue(assignment.get("parkingSpaceId")),
                    null,
                    month.toString(),
                    BigDecimal.ONE,
                    "月",
                    unitPrice,
                    unitPrice.setScale(2, RoundingMode.HALF_UP),
                    snapshot,
                    operatorId,
                    "停车月租费用依据"
            );
            confirmed++;
        }
        return confirmed;
    }

    @Transactional
    public Long confirmMeetingBasis(Long bookingId, Long operatorId) {
        Map<String, Object> booking = requireOne(
                """
                SELECT b.id,
                       b.tenant_id AS tenantId,
                       b.meeting_room_id AS meetingRoomId,
                       b.start_time AS startTime,
                       b.end_time AS endTime,
                       b.duration_minutes AS durationMinutes,
                       b.unit_price_snapshot AS unitPrice,
                       b.amount,
                       b.pricing_rule_id AS pricingRuleId,
                       b.purpose,
                       r.code AS roomCode,
                       r.name AS roomName
                FROM meeting_booking b
                JOIN meeting_room r ON r.id = b.meeting_room_id AND r.deleted = 0
                WHERE b.id = ? AND b.deleted = 0
                FOR UPDATE
                """,
                bookingId,
                "会议预约不存在"
        );
        Long tenantId = longValue(booking.get("tenantId"));
        if (tenantId == null) {
            throw new BusinessException(409, "内部员工免费预约不形成租户应收依据");
        }
        BigDecimal durationHours = decimal(booking.get("durationMinutes"))
                .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("roomCode", booking.get("roomCode"));
        snapshot.put("roomName", booking.get("roomName"));
        snapshot.put("startTime", booking.get("startTime"));
        snapshot.put("endTime", booking.get("endTime"));
        snapshot.put("pricingRuleId", booking.get("pricingRuleId"));
        snapshot.put("purpose", booking.get("purpose"));
        Long basisId = upsertConfirmedBasis(
                "MEETING",
                bookingId,
                tenantId,
                null,
                null,
                null,
                null,
                longValue(booking.get("meetingRoomId")),
                String.valueOf(booking.get("startTime")).substring(0, 10),
                durationHours,
                "小时",
                decimal(booking.get("unitPrice")),
                decimal(booking.get("amount")),
                snapshot,
                operatorId,
                "会议预约费用依据"
        );
        jdbcTemplate.update(
                "UPDATE meeting_booking SET charge_basis_id = ?, updated_by = ? WHERE id = ? AND deleted = 0",
                basisId,
                operatorId,
                bookingId
        );
        return basisId;
    }

    @Transactional
    public int syncPropertyBases(String billingPeriod, Long operatorId) {
        YearMonth month = parseMonth(billingPeriod);
        LocalDate firstDay = month.atDay(1);
        LocalDate lastDay = month.atEndOfMonth();
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
                """
                SELECT item_record.id,
                       plan_record.tenant_id AS tenantId,
                       plan_record.contract_id AS contractId,
                       plan_record.plan_no AS planNo,
                       item_record.item_name AS itemName,
                       item_record.quantity,
                       COALESCE(item_record.unit_price, item_record.fixed_amount, 0) AS unitPrice,
                       item_record.amount,
                       version_record.calculation_type AS calculationType,
                       rule_record.fee_type AS feeType,
                       rule_record.rule_code AS ruleCode,
                       version_record.version_no AS ruleVersionNo,
                       version_record.currency
                FROM billing_plan_item item_record
                JOIN billing_plan plan_record
                  ON plan_record.id = item_record.billing_plan_id
                 AND plan_record.deleted = 0
                 AND plan_record.status = 'ACTIVE'
                JOIN fee_rule_version version_record ON version_record.id = item_record.fee_rule_version_id
                JOIN fee_rule rule_record
                  ON rule_record.id = version_record.fee_rule_id
                 AND rule_record.deleted = 0
                  AND rule_record.fee_type IN ('PROPERTY', 'RENT')
                WHERE plan_record.effective_from <= ?
                  AND plan_record.effective_to >= ?
                ORDER BY item_record.id
                FOR UPDATE
                """,
                Date.valueOf(lastDay),
                Date.valueOf(firstDay)
        );
        int confirmed = 0;
        for (Map<String, Object> item : items) {
            Long contractId = longValue(item.get("contractId"));
            List<Map<String, Object>> rooms = contractRoomSnapshots(contractId);
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("planNo", item.get("planNo"));
            snapshot.put("itemName", item.get("itemName"));
            snapshot.put("calculationType", item.get("calculationType"));
            snapshot.put("feeType", item.get("feeType"));
            snapshot.put("ruleCode", item.get("ruleCode"));
            snapshot.put("ruleVersionNo", item.get("ruleVersionNo"));
            snapshot.put("currency", item.get("currency"));
            snapshot.put("rooms", rooms);
            BigDecimal quantity = decimal(item.get("quantity"));
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                quantity = BigDecimal.ONE;
            }
            upsertConfirmedBasis(
                    "RENT".equals(item.get("feeType")) ? "RENT" : "PROPERTY",
                    longValue(item.get("id")),
                    longValue(item.get("tenantId")),
                    contractId,
                    rooms.size() == 1 ? longValue(rooms.getFirst().get("roomId")) : null,
                    null,
                    null,
                    null,
                    month.toString(),
                    quantity,
                    "AREA_UNIT_PRICE".equals(item.get("calculationType")) ? "㎡" : "项",
                    decimal(item.get("unitPrice")),
                    decimal(item.get("amount")),
                    snapshot,
                    operatorId,
                    "RENT".equals(item.get("feeType")) ? "租金计费计划依据" : "物业费计费计划依据"
            );
            confirmed++;
        }
        return confirmed;
    }

    public List<Map<String, Object>> list(String status, String sourceType, String billingPeriod, Long tenantId) {
        StringBuilder sql = new StringBuilder(
                """
                SELECT basis.id,
                       basis.code,
                       basis.source_type AS sourceType,
                       basis.source_id AS sourceId,
                       basis.tenant_id AS tenantId,
                       tenant_record.name AS tenantName,
                       basis.contract_id AS contractId,
                       basis.room_id AS roomId,
                       basis.meter_id AS meterId,
                       basis.parking_space_id AS parkingSpaceId,
                       basis.meeting_room_id AS meetingRoomId,
                       basis.billing_period AS billingPeriod,
                       basis.quantity,
                       basis.unit,
                       basis.unit_price_snapshot AS unitPriceSnapshot,
                       basis.amount,
                       basis.charge_item_type AS chargeItemType,
                       basis.source_business_no AS sourceBusinessNo,
                       basis.currency,
                       basis.calculation_formula AS calculationFormula,
                       basis.status,
                       basis.bill_id AS billId,
                       bill_record.code AS billNumber,
                       basis.snapshot_json AS snapshot,
                       basis.confirmed_at AS confirmedAt,
                       basis.billed_at AS billedAt,
                       basis.remark
                FROM asset_charge_basis basis
                LEFT JOIN tenant tenant_record ON tenant_record.id = basis.tenant_id AND tenant_record.deleted = 0
                LEFT JOIN bill bill_record ON bill_record.id = basis.bill_id AND bill_record.deleted = 0
                WHERE basis.deleted = 0
                """
        );
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND basis.status = ?");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
        if (sourceType != null && !sourceType.isBlank()) {
            sql.append(" AND basis.source_type = ?");
            args.add(sourceType.trim().toUpperCase(Locale.ROOT));
        }
        if (billingPeriod != null && !billingPeriod.isBlank()) {
            sql.append(" AND basis.billing_period = ?");
            args.add(billingPeriod.trim());
        }
        if (tenantId != null) {
            sql.append(" AND basis.tenant_id = ?");
            args.add(tenantId);
        }
        sql.append(" ORDER BY basis.billing_period DESC, basis.id DESC");
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    @Transactional
    public Map<String, Object> generateBills(List<Long> basisIds, LocalDate dueDate, Long operatorId) {
        if (basisIds == null || basisIds.isEmpty()) {
            throw new BusinessException(400, "请选择至少一条已确认计费依据");
        }
        if (operatorId == null) {
            throw BusinessException.unauthorized("登录状态已失效");
        }
        List<Long> distinctIds = basisIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (distinctIds.isEmpty()) {
            throw new BusinessException(400, "请选择至少一条已确认计费依据");
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(distinctIds.size(), "?"));
        List<Map<String, Object>> bases = jdbcTemplate.queryForList(
                "SELECT * FROM asset_charge_basis WHERE id IN (" + placeholders + ") "
                        + "AND deleted = 0 ORDER BY id FOR UPDATE",
                distinctIds.toArray()
        );
        if (bases.size() != distinctIds.size()) {
            throw new BusinessException(404, "部分计费依据不存在");
        }
        List<Long> existingBillIds = bases.stream()
                .map(row -> longValue(row.get("bill_id")))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (!existingBillIds.isEmpty()) {
            if (existingBillIds.size() == 1
                    && bases.stream().allMatch(row -> "BILLED".equals(row.get("status")))) {
                return Map.of(
                        "candidateCount", distinctIds.size(),
                        "generatedCount", 0,
                        "failureCount", 0,
                        "billId", existingBillIds.getFirst(),
                        "idempotent", true,
                        "failures", List.of()
                );
            }
            throw new BusinessException(409, "所选依据包含已出账记录，不能重复或跨账单合并");
        }
        if (bases.stream().anyMatch(row -> !"CONFIRMED".equals(row.get("status")))) {
            throw new BusinessException(409, "只有已确认且未消费的计费依据可以生成账单");
        }
        List<Long> tenantIds = bases.stream()
                .map(row -> longValue(row.get("tenant_id")))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (tenantIds.size() != 1 || bases.stream().anyMatch(row -> row.get("tenant_id") == null)) {
            throw new BusinessException(409, "不同租户或物业公共依据不能合并为同一租户账单");
        }

        Long tenantId = tenantIds.getFirst();
        List<Long> contractIds = bases.stream()
                .map(row -> longValue(row.get("contract_id")))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Long contractId = contractIds.size() == 1 ? contractIds.getFirst() : null;
        BigDecimal calculatedTotal = bases.stream()
                .map(row -> decimal(row.get("amount")))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        if (calculatedTotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(409, "零金额计费依据不能生成应收账单");
        }
        LocalDate periodStart = bases.stream()
                .map(row -> billingDates(stringValue(row.get("billing_period")))[0])
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now());
        LocalDate periodEnd = bases.stream()
                .map(row -> billingDates(stringValue(row.get("billing_period")))[1])
                .max(LocalDate::compareTo)
                .orElse(periodStart);
        String billingPeriod = periodStart.getYear() == periodEnd.getYear()
                && periodStart.getMonth() == periodEnd.getMonth()
                ? YearMonth.from(periodStart).toString()
                : periodStart + "/" + periodEnd;
        List<String> chargeTypes = bases.stream()
                .map(row -> chargeItemType(stringValue(row.get("source_type")), row))
                .distinct()
                .toList();
        String billType = chargeTypes.size() == 1 ? chargeTypes.getFirst() : "COMPOSITE";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO bill (
                        code, tenant_id, contract_id, source_type, source_id, bill_type, title,
                        amount, calculated_total, adjustment_total, final_amount, paid_amount,
                        period_start, period_end, billing_period, bill_date, due_date,
                        audit_status, workflow_status, pay_status, invoice_status, version,
                        status, created_by, updated_by, remark
                    )
                    VALUES (?, ?, ?, 'CHARGE_BASIS', NULL, ?, ?, ?, ?, 0, ?, 0,
                            ?, ?, ?, CURRENT_DATE, ?,
                            'PENDING', 'DRAFT', 'UNPAID', 'NOT_INVOICED', 0,
                            'ACTIVE', ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, JdbcMaps.code("BIL"));
            statement.setLong(2, tenantId);
            statement.setObject(3, contractId);
            statement.setString(4, billType);
            statement.setString(5, title("COMPOSITE", billingPeriod));
            statement.setBigDecimal(6, calculatedTotal);
            statement.setBigDecimal(7, calculatedTotal);
            statement.setBigDecimal(8, calculatedTotal);
            statement.setDate(9, Date.valueOf(periodStart));
            statement.setDate(10, Date.valueOf(periodEnd));
            statement.setString(11, billingPeriod);
            statement.setDate(12, Date.valueOf(dueDate == null ? periodEnd.plusDays(15) : dueDate));
            statement.setLong(13, operatorId);
            statement.setLong(14, operatorId);
            statement.setString(15, "由已确认计费依据合并生成，来源快照不可在财务模块篡改");
            return statement;
        }, keyHolder);
        Long billId = keyHolder.getKey().longValue();

        int sortOrder = 0;
        for (Map<String, Object> basis : bases) {
            jdbcTemplate.update(
                    """
                    INSERT INTO bill_line_item (
                        bill_id, charge_basis_id, charge_item_type, source_type, source_id,
                        source_snapshot, description, quantity_snapshot, unit, unit_price_snapshot,
                        calculated_amount, adjustment_amount, final_amount, adjustment_reason,
                        sort_order, status, created_by, updated_by
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, NULL, ?, 'ACTIVE', ?, ?)
                    """,
                    billId,
                    basis.get("id"),
                    chargeItemType(stringValue(basis.get("source_type")), basis),
                    basis.get("source_type"),
                    basis.get("source_id"),
                    basis.get("snapshot_json"),
                    description(basis),
                    basis.get("quantity"),
                    basis.get("unit"),
                    basis.get("unit_price_snapshot"),
                    basis.get("amount"),
                    basis.get("amount"),
                    sortOrder++,
                    operatorId,
                    operatorId
            );
        }
        Object[] updateArgs = new Object[2 + distinctIds.size()];
        updateArgs[0] = billId;
        updateArgs[1] = operatorId;
        for (int index = 0; index < distinctIds.size(); index++) {
            updateArgs[index + 2] = distinctIds.get(index);
        }
        int consumed = jdbcTemplate.update(
                "UPDATE asset_charge_basis SET status = 'BILLED', bill_id = ?, "
                        + "billed_at = CURRENT_TIMESTAMP, updated_by = ? "
                        + "WHERE id IN (" + placeholders + ") "
                        + "AND status = 'CONFIRMED' AND bill_id IS NULL AND deleted = 0",
                updateArgs
        );
        if (consumed != distinctIds.size()) {
            throw new BusinessException(409, "计费依据已被其他财务人员消费，请刷新后重试");
        }
        for (Map<String, Object> basis : bases) {
            updateSourceAfterBilling(stringValue(basis.get("source_type")), basis, billId, operatorId);
        }
        jdbcTemplate.update(
                """
                INSERT INTO bill_audit_log (
                    bill_id, action, before_snapshot, after_snapshot, reason, operator_id
                )
                VALUES (?, 'GENERATE_BILL', NULL, ?, '合并已确认计费依据生成草稿账单', ?)
                """,
                billId,
                json(Map.of(
                        "basisIds", distinctIds,
                        "calculatedTotal", calculatedTotal,
                        "workflowStatus", "DRAFT"
                )),
                operatorId
        );
        return Map.of(
                "candidateCount", distinctIds.size(),
                "generatedCount", 1,
                "failureCount", 0,
                "billId", billId,
                "idempotent", false,
                "failures", List.of()
        );
    }

    @Transactional
    public void cancelUnbilledBasis(String sourceType, Long sourceId, Long operatorId, String reason) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, status, bill_id AS billId
                FROM asset_charge_basis
                WHERE source_type = ? AND source_id = ? AND deleted = 0
                  AND status <> 'CANCELLED'
                ORDER BY id DESC
                LIMIT 1
                FOR UPDATE
                """,
                sourceType,
                sourceId
        );
        if (rows.isEmpty()) {
            return;
        }
        Map<String, Object> basis = rows.getFirst();
        if ("BILLED".equals(basis.get("status")) || basis.get("billId") != null) {
            throw new BusinessException(409, "计费依据已生成财务账单，必须在财务模块按账单状态处理");
        }
        jdbcTemplate.update(
                """
                UPDATE asset_charge_basis
                SET status = 'CANCELLED',
                    updated_by = ?,
                    remark = CASE
                        WHEN ? IS NULL OR ? = '' THEN remark
                        ELSE CONCAT(COALESCE(remark, ''), '；取消原因：', ?)
                    END
                WHERE id = ? AND deleted = 0
                """,
                operatorId,
                reason,
                reason,
                reason,
                basis.get("id")
        );
    }

    private void generateBill(Long basisId, LocalDate dueDate, Long operatorId) {
        Map<String, Object> basis = requireOne(
                """
                SELECT *
                FROM asset_charge_basis
                WHERE id = ? AND deleted = 0
                FOR UPDATE
                """,
                basisId,
                "计费依据不存在"
        );
        if ("BILLED".equals(basis.get("status")) && basis.get("bill_id") != null) {
            return;
        }
        if (!"CONFIRMED".equals(basis.get("status"))) {
            throw new BusinessException(409, "只有已确认的计费依据可以生成账单");
        }
        if (basis.get("tenant_id") == null) {
            throw new BusinessException(409, "物业计量主体不生成租户应收账单");
        }
        BigDecimal amount = decimal(basis.get("amount"));
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(409, "零金额计费依据不生成应收账单");
        }

        LocalDate[] period = billingDates(stringValue(basis.get("billing_period")));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO bill (
                        code, tenant_id, contract_id, source_type, source_id, bill_type, title,
                        amount, paid_amount, period_start, period_end, due_date,
                        audit_status, pay_status, status, created_by, updated_by, remark
                    )
                    VALUES (?, ?, ?, 'ASSET_BASIS', ?, ?, ?, ?, 0, ?, ?, ?,
                            'PENDING', 'UNPAID', 'ACTIVE', ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, JdbcMaps.code("BIL"));
            statement.setObject(2, basis.get("tenant_id"));
            statement.setObject(3, basis.get("contract_id"));
            statement.setLong(4, basisId);
            statement.setString(5, billType(stringValue(basis.get("source_type"))));
            statement.setString(6, title(stringValue(basis.get("source_type")), stringValue(basis.get("billing_period"))));
            statement.setBigDecimal(7, amount);
            statement.setDate(8, Date.valueOf(period[0]));
            statement.setDate(9, Date.valueOf(period[1]));
            statement.setDate(10, Date.valueOf(dueDate == null ? period[1].plusDays(15) : dueDate));
            statement.setLong(11, operatorId);
            statement.setLong(12, operatorId);
            statement.setString(13, "由资产计费依据统一生成，历史价格与数量以依据快照为准");
            return statement;
        }, keyHolder);
        Long billId = keyHolder.getKey().longValue();
        jdbcTemplate.update(
                """
                UPDATE asset_charge_basis
                SET status = 'BILLED', bill_id = ?, billed_at = CURRENT_TIMESTAMP, updated_by = ?
                WHERE id = ? AND status = 'CONFIRMED' AND deleted = 0
                """,
                billId,
                operatorId,
                basisId
        );
        updateSourceAfterBilling(stringValue(basis.get("source_type")), basis, billId, operatorId);
    }

    private void updateSourceAfterBilling(
            String sourceType,
            Map<String, Object> basis,
            Long billId,
            Long operatorId
    ) {
        if ("ENERGY".equals(sourceType)) {
            jdbcTemplate.update(
                    """
                    UPDATE energy_reading
                    SET bill_id = ?, bill_status = 'GENERATED', updated_by = ?
                    WHERE id = ? AND charge_basis_id = ? AND deleted = 0
                    """,
                    billId,
                    operatorId,
                    basis.get("source_id"),
                    basis.get("id")
            );
            return;
        }
        if ("MEETING".equals(sourceType)) {
            jdbcTemplate.update(
                    """
                    INSERT INTO meeting_bill (
                        code, booking_id, bill_id, amount, status, created_by, updated_by, remark
                    )
                    VALUES (?, ?, ?, ?, 'ENABLED', ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        bill_id = VALUES(bill_id),
                        amount = VALUES(amount),
                        status = 'ENABLED',
                        deleted = 0,
                        updated_by = VALUES(updated_by),
                        remark = VALUES(remark)
                    """,
                    JdbcMaps.code("MBL"),
                    basis.get("source_id"),
                    billId,
                    basis.get("amount"),
                    operatorId,
                    operatorId,
                    "由统一资产计费依据生成"
            );
        }
    }

    private Long upsertConfirmedBasis(
            String sourceType,
            Long sourceId,
            Long tenantId,
            Long contractId,
            Long roomId,
            Long meterId,
            Long parkingSpaceId,
            Long meetingRoomId,
            String billingPeriod,
            BigDecimal quantity,
            String unit,
            BigDecimal unitPrice,
            BigDecimal amount,
            Map<String, Object> snapshot,
            Long operatorId,
            String remark
    ) {
        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                """
                SELECT id, status, bill_id AS billId
                FROM asset_charge_basis
                WHERE source_type = ?
                  AND source_id = ?
                  AND billing_period = ?
                  AND deleted = 0
                  AND status <> 'CANCELLED'
                FOR UPDATE
                """,
                sourceType,
                sourceId,
                billingPeriod
        );
        String snapshotJson = json(snapshot);
        if (!existing.isEmpty()) {
            Map<String, Object> basis = existing.getFirst();
            if ("BILLED".equals(basis.get("status")) || basis.get("billId") != null) {
                return longValue(basis.get("id"));
            }
            jdbcTemplate.update(
                    """
                    UPDATE asset_charge_basis
                    SET tenant_id = ?, contract_id = ?, room_id = ?, meter_id = ?,
                        parking_space_id = ?, meeting_room_id = ?, quantity = ?, unit = ?,
                        unit_price_snapshot = ?, amount = ?, charge_item_type = ?,
                        source_business_no = ?, currency = ?, calculation_formula = ?,
                        status = 'CONFIRMED', snapshot_json = ?,
                        source_created_at = COALESCE(source_created_at, CURRENT_TIMESTAMP),
                        confirmed_at = CURRENT_TIMESTAMP,
                        updated_by = ?, remark = ?
                    WHERE id = ? AND deleted = 0
                    """,
                    tenantId,
                    contractId,
                    roomId,
                    meterId,
                    parkingSpaceId,
                    meetingRoomId,
                    quantity,
                    unit,
                    unitPrice,
                    amount,
                    chargeItemType(sourceType, snapshot),
                    sourceBusinessNo(sourceType, sourceId, snapshot),
                    stringOr(snapshot.get("currency"), "CNY"),
                    formula(quantity, unit, unitPrice, amount),
                    snapshotJson,
                    operatorId,
                    remark,
                    basis.get("id")
            );
            return longValue(basis.get("id"));
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        """
                        INSERT INTO asset_charge_basis (
                            code, source_type, source_id, tenant_id, contract_id, room_id, meter_id,
                            parking_space_id, meeting_room_id, billing_period, quantity, unit,
                            unit_price_snapshot, amount, charge_item_type, source_business_no,
                            currency, calculation_formula, status, snapshot_json,
                            source_created_at, confirmed_at,
                            created_by, updated_by, remark
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                                'CONFIRMED', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?, ?)
                        """,
                        Statement.RETURN_GENERATED_KEYS
                );
                statement.setString(1, JdbcMaps.code("ACB"));
                statement.setString(2, sourceType);
                statement.setLong(3, sourceId);
                statement.setObject(4, tenantId);
                statement.setObject(5, contractId);
                statement.setObject(6, roomId);
                statement.setObject(7, meterId);
                statement.setObject(8, parkingSpaceId);
                statement.setObject(9, meetingRoomId);
                statement.setString(10, billingPeriod);
                statement.setBigDecimal(11, quantity);
                statement.setString(12, unit);
                statement.setBigDecimal(13, unitPrice);
                statement.setBigDecimal(14, amount);
                statement.setString(15, chargeItemType(sourceType, snapshot));
                statement.setString(16, sourceBusinessNo(sourceType, sourceId, snapshot));
                statement.setString(17, stringOr(snapshot.get("currency"), "CNY"));
                statement.setString(18, formula(quantity, unit, unitPrice, amount));
                statement.setString(19, snapshotJson);
                statement.setLong(20, operatorId);
                statement.setLong(21, operatorId);
                statement.setString(22, remark);
                return statement;
            }, keyHolder);
            return keyHolder.getKey().longValue();
        } catch (DuplicateKeyException exception) {
            List<Map<String, Object>> concurrent = jdbcTemplate.queryForList(
                    """
                    SELECT id
                    FROM asset_charge_basis
                    WHERE source_type = ? AND source_id = ? AND billing_period = ?
                      AND deleted = 0 AND status <> 'CANCELLED'
                    LIMIT 1
                    """,
                    sourceType,
                    sourceId,
                    billingPeriod
            );
            if (!concurrent.isEmpty()) {
                return longValue(concurrent.getFirst().get("id"));
            }
            throw exception;
        }
    }

    private Map<String, Object> activeMeterBinding(Long meterId, LocalDate readingDate) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id,
                       subject_type AS subjectType,
                       tenant_id AS tenantId,
                       room_id AS roomId,
                       effective_from AS effectiveFrom,
                       effective_to AS effectiveTo
                FROM energy_meter_binding
                WHERE meter_id = ?
                  AND deleted = 0
                  AND status IN ('ACTIVE', 'ENDED')
                  AND effective_from <= ?
                  AND (effective_to IS NULL OR effective_to >= ?)
                ORDER BY effective_from DESC, id DESC
                LIMIT 2
                FOR UPDATE
                """,
                meterId,
                Date.valueOf(readingDate),
                Date.valueOf(readingDate)
        );
        if (rows.isEmpty()) {
            throw new BusinessException(409, "抄表日期没有有效计量主体绑定");
        }
        if (rows.size() > 1) {
            throw new BusinessException(409, "表具计量主体绑定期间重叠，请先修复绑定关系");
        }
        return rows.getFirst();
    }

    private List<Map<String, Object>> contractRoomSnapshots(Long contractId) {
        List<Map<String, Object>> rooms = jdbcTemplate.queryForList(
                """
                SELECT room_record.id AS roomId,
                       room_record.code AS roomCode,
                       room_record.property_fee_area AS billingAreaSnapshot
                FROM (
                    SELECT room_id
                    FROM contract_room
                    WHERE contract_id = ? AND deleted = 0
                    UNION
                    SELECT room_id
                    FROM contract
                    WHERE id = ? AND deleted = 0
                ) linked_room
                JOIN building_room room_record
                  ON room_record.id = linked_room.room_id
                 AND room_record.deleted = 0
                ORDER BY room_record.id
                """,
                contractId,
                contractId
        );
        if (rooms.isEmpty()) {
            throw new BusinessException(409, "合同没有可用房间，无法形成物业费计费依据");
        }
        return rooms;
    }

    private Map<String, Object> requireOne(String sql, Object id, String message) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, id);
        if (rows.isEmpty()) {
            throw new BusinessException(404, message);
        }
        return rows.getFirst();
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("计费依据快照序列化失败", exception);
        }
    }

    private YearMonth parseMonth(String value) {
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(400, "账期格式必须为 YYYY-MM");
        }
    }

    private LocalDate[] billingDates(String billingPeriod) {
        try {
            YearMonth month = YearMonth.parse(billingPeriod);
            return new LocalDate[]{month.atDay(1), month.atEndOfMonth()};
        } catch (DateTimeParseException ignored) {
            try {
                LocalDate date = LocalDate.parse(billingPeriod.substring(0, 10));
                return new LocalDate[]{date, date};
            } catch (RuntimeException exception) {
                throw new BusinessException(409, "计费依据账期格式不正确");
            }
        }
    }

    private String billType(String sourceType) {
        return switch (sourceType) {
            case "RENT" -> "RENT";
            case "PROPERTY" -> "PROPERTY";
            case "ENERGY" -> "ENERGY";
            case "PARKING" -> "PARKING";
            case "MEETING" -> "MEETING";
            default -> "OTHER";
        };
    }

    private String title(String sourceType, String billingPeriod) {
        String label = switch (sourceType) {
            case "RENT" -> "租金";
            case "PROPERTY" -> "物业费";
            case "ENERGY" -> "能耗费";
            case "PARKING" -> "停车费";
            case "MEETING" -> "会议室费用";
            case "COMPOSITE" -> "综合账单";
            default -> "资产服务费";
        };
        return billingPeriod + " " + label;
    }

    private String chargeItemType(String sourceType, Map<String, Object> values) {
        Object explicit = values.get("charge_item_type");
        if (explicit != null && !String.valueOf(explicit).isBlank()) {
            return String.valueOf(explicit);
        }
        Map<String, Object> snapshot = values.containsKey("snapshot_json")
                ? mapJson(values.get("snapshot_json"))
                : values;
        return switch (sourceType) {
            case "RENT" -> "RENT";
            case "PROPERTY" -> "PROPERTY_FEE";
            case "ENERGY" -> switch (stringValue(snapshot.get("meterType")).toUpperCase(Locale.ROOT)) {
                case "WATER" -> "WATER";
                case "GAS" -> "GAS";
                default -> "ELECTRICITY";
            };
            case "PARKING" -> "PARKING";
            case "MEETING" -> "MEETING";
            default -> "OTHER";
        };
    }

    private String sourceBusinessNo(String sourceType, Long sourceId, Map<String, Object> snapshot) {
        for (String key : List.of("meterNo", "spaceNo", "roomCode", "ruleCode", "planNo")) {
            Object value = snapshot.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return sourceType + "-" + sourceId;
    }

    private String formula(BigDecimal quantity, String unit, BigDecimal unitPrice, BigDecimal amount) {
        return quantity.stripTrailingZeros().toPlainString() + " " + unit
                + " × " + unitPrice.stripTrailingZeros().toPlainString()
                + " = " + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String description(Map<String, Object> basis) {
        return chargeItemType(stringValue(basis.get("source_type")), basis)
                + " " + stringValue(basis.get("billing_period"))
                + "（来源 " + stringValue(basis.get("source_business_no")) + "）";
    }

    private String stringOr(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapJson(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(String.valueOf(value), Map.class);
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Long.valueOf(String.valueOf(value));
    }

    private LocalDate localDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value).substring(0, 10));
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
