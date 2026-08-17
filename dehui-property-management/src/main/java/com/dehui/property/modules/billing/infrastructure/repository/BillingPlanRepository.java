package com.dehui.property.modules.billing.infrastructure.repository;

import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcPagination;
import com.dehui.property.common.PageResponse;
import com.dehui.property.common.WorkspaceOrderBy;
import com.dehui.property.modules.customfield.application.CustomFieldQueryCompiler.CompiledQuery;
import com.dehui.property.modules.customfield.application.CustomFieldQueryCompiler.NativeOrderPolicy;
import com.dehui.property.modules.customfield.application.CustomFieldQueryService;
import com.dehui.property.modules.customfield.application.CustomFieldProjectionService;
import com.dehui.property.modules.billing.domain.model.BillingCycle;
import com.dehui.property.modules.billing.domain.model.BillingPlanStatus;
import com.dehui.property.modules.billing.domain.model.CalculationType;
import com.dehui.property.modules.billing.domain.model.DueRule;
import com.dehui.property.modules.billing.dto.BillingPlanCreateRequest;
import com.dehui.property.modules.billing.dto.BillingPlanItemResponse;
import com.dehui.property.modules.billing.dto.BillingPlanResponse;
import com.dehui.property.modules.billing.dto.BillingPlanSummaryResponse;
import com.dehui.property.modules.billing.dto.BillingPlanUpdateRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class BillingPlanRepository {

    private static final String SELECT = """
            SELECT plan_record.id, plan_record.plan_no, plan_record.plan_name,
                   plan_record.tenant_id, tenant_record.name AS tenant_name,
                   plan_record.contract_id, contract_record.code AS contract_no,
                   plan_record.lease_id, lease_record.lease_no,
                   plan_record.effective_from, plan_record.effective_to,
                   plan_record.billing_cycle, plan_record.billing_lead_days,
                   plan_record.due_rule, plan_record.currency, plan_record.status,
                   COALESCE(item_totals.item_count, 0) AS item_count,
                   COALESCE(item_totals.total_amount, 0) AS estimated_period_amount,
                   plan_record.version, plan_record.created_at, plan_record.updated_at
            FROM billing_plan plan_record
            JOIN tenant tenant_record ON tenant_record.id = plan_record.tenant_id AND tenant_record.deleted = 0
            JOIN contract contract_record ON contract_record.id = plan_record.contract_id AND contract_record.deleted = 0
            LEFT JOIN lease lease_record ON lease_record.id = plan_record.lease_id AND lease_record.deleted = 0
            LEFT JOIN (
              SELECT billing_plan_id, COUNT(*) AS item_count, SUM(amount) AS total_amount
              FROM billing_plan_item
              GROUP BY billing_plan_id
            ) item_totals ON item_totals.billing_plan_id = plan_record.id
            """;

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "planNo", "plan_record.plan_no",
            "planName", "plan_record.plan_name",
            "tenantName", "tenant_record.name",
            "contractNo", "contract_record.code",
            "status", "plan_record.status",
            "effectiveFrom", "plan_record.effective_from",
            "effectiveTo", "plan_record.effective_to",
            "updatedAt", "plan_record.updated_at"
    );

    private final JdbcTemplate jdbcTemplate;
    private final CustomFieldQueryService customFieldQueryService;
    private final CustomFieldProjectionService customFieldProjectionService;

    public BillingPlanRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null, null);
    }

    public BillingPlanRepository(JdbcTemplate jdbcTemplate, CustomFieldQueryService customFieldQueryService) {
        this(jdbcTemplate, customFieldQueryService, null);
    }

    @Autowired
    public BillingPlanRepository(
            JdbcTemplate jdbcTemplate,
            CustomFieldQueryService customFieldQueryService,
            CustomFieldProjectionService customFieldProjectionService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.customFieldQueryService = customFieldQueryService;
        this.customFieldProjectionService = customFieldProjectionService;
    }

    public PageResponse<BillingPlanResponse> search(
            String keyword,
            BillingPlanStatus status,
            Long tenantId,
            Long contractId,
            Long leaseId,
            String sortField,
            String sortDirection,
            Integer page,
            Integer pageSize
    ) {
        return search(
                keyword, status, tenantId, contractId, leaseId,
                sortField, sortDirection, null, null, null, page, pageSize
        );
    }

    public PageResponse<BillingPlanResponse> search(
            String keyword,
            BillingPlanStatus status,
            Long tenantId,
            Long contractId,
            Long leaseId,
            String sortField,
            String sortDirection,
            String sorts,
            String groupBy,
            String customFilters,
            Integer page,
            Integer pageSize
    ) {
        Filter filter = filter(keyword, status, tenantId, contractId, leaseId);
        String where = filter.where();
        List<Object> args = new ArrayList<>(filter.args());
        CompiledQuery customQuery = compileCustomQuery(
                customFilters,
                groupBy,
                sorts,
                NativeOrderPolicy.of(
                        SORT_FIELDS, sortField, sortDirection, "plan_record.updated_at", "plan_record.id"
                )
        );
        if (customQuery != null && customQuery.hasPredicates()) {
            where += " AND " + customQuery.predicateSql();
            args.addAll(customQuery.predicateArguments());
        }
        String orderBy = customQuery == null || !customQuery.hasOrderings()
                ? WorkspaceOrderBy.build(
                        SORT_FIELDS, groupBy, sorts, sortField, sortDirection,
                        "plan_record.updated_at", "plan_record.id"
                )
                : String.join(", ", customQuery.orderExpressions());
        PageResponse<BillingPlanResponse> pageResponse = JdbcPagination.query(
                jdbcTemplate,
                SELECT + where + " ORDER BY " + orderBy,
                """
                SELECT COUNT(*)
                FROM billing_plan plan_record
                JOIN tenant tenant_record ON tenant_record.id = plan_record.tenant_id AND tenant_record.deleted = 0
                JOIN contract contract_record ON contract_record.id = plan_record.contract_id AND contract_record.deleted = 0
                LEFT JOIN lease lease_record ON lease_record.id = plan_record.lease_id AND lease_record.deleted = 0
                """ + where,
                args,
                customQuery == null ? List.of() : customQuery.orderArguments(),
                page,
                pageSize,
                responseMapper(false)
        );
        return customFieldProjectionService == null
                ? pageResponse
                : customFieldProjectionService.attach(
                        "billing-plans", pageResponse, BillingPlanResponse::id, BillingPlanResponse::withCustomFields
                );
    }

    public PageResponse<BillingPlanResponse> search(
            String keyword,
            BillingPlanStatus status,
            Long tenantId,
            Long contractId,
            Long leaseId,
            String sortField,
            String sortDirection,
            String sorts,
            String groupBy,
            Integer page,
            Integer pageSize
    ) {
        return search(
                keyword, status, tenantId, contractId, leaseId, sortField, sortDirection,
                sorts, groupBy, null, page, pageSize
        );
    }

    public BillingPlanSummaryResponse summary(
            String keyword,
            BillingPlanStatus status,
            Long tenantId,
            Long contractId,
            Long leaseId
    ) {
        return summary(keyword, status, tenantId, contractId, leaseId, null);
    }

    public BillingPlanSummaryResponse summary(
            String keyword,
            BillingPlanStatus status,
            Long tenantId,
            Long contractId,
            Long leaseId,
            String customFilters
    ) {
        Filter filter = filter(keyword, status, tenantId, contractId, leaseId);
        String where = filter.where();
        List<Object> args = new ArrayList<>(filter.args());
        CompiledQuery customQuery = compileCustomQuery(customFilters, null, null, null);
        if (customQuery != null && customQuery.hasPredicates()) {
            where += " AND " + customQuery.predicateSql();
            args.addAll(customQuery.predicateArguments());
        }
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) AS total_count,
                       COALESCE(SUM(CASE WHEN plan_record.status = 'DRAFT' THEN 1 ELSE 0 END), 0) AS draft_count,
                       COALESCE(SUM(CASE WHEN plan_record.status = 'ACTIVE' THEN 1 ELSE 0 END), 0) AS active_count,
                       COALESCE(SUM(CASE WHEN plan_record.status = 'TERMINATED' THEN 1 ELSE 0 END), 0) AS terminated_count,
                       COALESCE(SUM(COALESCE(item_totals.total_amount, 0)), 0) AS estimated_period_amount
                FROM billing_plan plan_record
                JOIN tenant tenant_record ON tenant_record.id = plan_record.tenant_id AND tenant_record.deleted = 0
                JOIN contract contract_record ON contract_record.id = plan_record.contract_id AND contract_record.deleted = 0
                LEFT JOIN lease lease_record ON lease_record.id = plan_record.lease_id AND lease_record.deleted = 0
                LEFT JOIN (
                  SELECT billing_plan_id, SUM(amount) AS total_amount
                  FROM billing_plan_item
                  GROUP BY billing_plan_id
                ) item_totals ON item_totals.billing_plan_id = plan_record.id
                """ + where,
                (rs, rowNum) -> new BillingPlanSummaryResponse(
                        rs.getLong("total_count"),
                        rs.getLong("draft_count"),
                        rs.getLong("active_count"),
                        rs.getLong("terminated_count"),
                        money(rs.getBigDecimal("estimated_period_amount"))
                ),
                args.toArray()
        );
    }

    private CompiledQuery compileCustomQuery(
            String customFilters,
            String groupBy,
            String sorts,
            NativeOrderPolicy orderPolicy
    ) {
        if (customFieldQueryService == null) return null;
        return customFieldQueryService.compile(
                "billing-plans",
                customFilters,
                groupBy,
                sorts,
                orderPolicy
        );
    }

    public BillingPlanResponse find(Long id) {
        List<BillingPlanResponse> rows = jdbcTemplate.query(
                SELECT + " WHERE plan_record.id = ? AND plan_record.deleted = 0",
                responseMapper(false),
                id
        );
        if (rows.isEmpty()) throw new BusinessException(404, "计费计划不存在");
        BillingPlanResponse header = rows.getFirst();
        return new BillingPlanResponse(
                header.id(), header.planNo(), header.planName(), header.tenantId(), header.tenantName(),
                header.contractId(), header.contractNo(), header.leaseId(), header.leaseNo(),
                header.effectiveFrom(), header.effectiveTo(), header.billingCycle(), header.billingLeadDays(),
                header.dueRule(), header.currency(), header.status(), header.itemCount(), header.estimatedPeriodAmount(),
                header.version(), header.createdAt(), header.updatedAt(), items(header.id())
        );
    }

    public BillingPlanSnapshot lock(Long id) {
        List<BillingPlanSnapshot> rows = jdbcTemplate.query(
                """
                SELECT id, plan_no, status, version
                FROM billing_plan
                WHERE id = ? AND deleted = 0
                FOR UPDATE
                """,
                (rs, rowNum) -> new BillingPlanSnapshot(
                        rs.getLong("id"),
                        rs.getString("plan_no"),
                        BillingPlanStatus.valueOf(rs.getString("status")),
                        rs.getLong("version")
                ),
                id
        );
        if (rows.isEmpty()) throw new BusinessException(404, "计费计划不存在");
        return rows.getFirst();
    }

    public ContractLeaseBounds relationBounds(Long tenantId, Long contractId, Long leaseId) {
        String sql = """
                SELECT contract_record.tenant_id,
                       contract_record.start_date AS contract_start,
                       contract_record.end_date AS contract_end,
                       lease_record.id AS lease_id,
                       lease_record.planned_start_date AS lease_start,
                       lease_record.planned_end_date AS lease_end,
                       lease_record.status AS lease_status
                FROM contract contract_record
                LEFT JOIN lease lease_record
                  ON lease_record.id = ?
                 AND lease_record.contract_id = contract_record.id
                 AND lease_record.tenant_id = contract_record.tenant_id
                 AND lease_record.deleted = 0
                WHERE contract_record.id = ?
                  AND contract_record.tenant_id = ?
                  AND contract_record.deleted = 0
                """;
        List<ContractLeaseBounds> rows = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new ContractLeaseBounds(
                        rs.getLong("tenant_id"),
                        rs.getObject("contract_start", LocalDate.class),
                        rs.getObject("contract_end", LocalDate.class),
                        rs.getObject("lease_id", Long.class),
                        rs.getObject("lease_start", LocalDate.class),
                        rs.getObject("lease_end", LocalDate.class),
                        rs.getString("lease_status")
                ),
                leaseId, contractId, tenantId
        );
        if (rows.isEmpty()) throw new BusinessException(400, "租户与合同关系无效");
        ContractLeaseBounds bounds = rows.getFirst();
        if (leaseId != null && bounds.leaseId() == null) {
            throw new BusinessException(400, "租约不属于指定租户与合同");
        }
        return bounds;
    }

    public Long create(
            BillingPlanCreateRequest request,
            List<ResolvedBillingPlanItem> items,
            Long operatorId
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        """
                        INSERT INTO billing_plan (
                            plan_no, plan_name, tenant_id, contract_id, lease_id,
                            effective_from, effective_to, billing_cycle, billing_lead_days,
                            due_rule, currency, status, version, created_by, updated_by
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', 0, ?, ?)
                        """,
                        Statement.RETURN_GENERATED_KEYS
                );
                statement.setString(1, request.planNo().trim().toUpperCase(java.util.Locale.ROOT));
                statement.setString(2, request.planName().trim());
                statement.setLong(3, request.tenantId());
                statement.setLong(4, request.contractId());
                if (request.leaseId() == null) statement.setObject(5, null); else statement.setLong(5, request.leaseId());
                statement.setDate(6, Date.valueOf(request.effectiveFrom()));
                statement.setDate(7, Date.valueOf(request.effectiveTo()));
                statement.setString(8, request.billingCycle().name());
                statement.setInt(9, request.billingLeadDays());
                statement.setString(10, request.dueRule().name());
                statement.setString(11, request.currency().toUpperCase(java.util.Locale.ROOT));
                statement.setLong(12, operatorId);
                statement.setLong(13, operatorId);
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "计费计划编号已存在");
        }
        Long id = generatedId(keyHolder);
        insertItems(id, items, operatorId);
        return id;
    }

    public BigDecimal propertyFeeArea(Long contractId) {
        BigDecimal area = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(room_record.property_fee_area), 0)
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
                """,
                BigDecimal.class,
                contractId,
                contractId
        );
        if (area == null || area.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(409, "合同房间未配置有效物业费计费面积");
        }
        return area.setScale(4, RoundingMode.HALF_UP);
    }

    public void update(
            BillingPlanSnapshot current,
            BillingPlanUpdateRequest request,
            List<ResolvedBillingPlanItem> items,
            Long operatorId
    ) {
        int updated = jdbcTemplate.update(
                """
                UPDATE billing_plan
                SET plan_name = ?, tenant_id = ?, contract_id = ?, lease_id = ?,
                    effective_from = ?, effective_to = ?, billing_cycle = ?,
                    billing_lead_days = ?, due_rule = ?, currency = ?,
                    version = version + 1, updated_by = ?
                WHERE id = ? AND version = ? AND status = 'DRAFT' AND deleted = 0
                """,
                request.planName().trim(), request.tenantId(), request.contractId(), request.leaseId(),
                Date.valueOf(request.effectiveFrom()), Date.valueOf(request.effectiveTo()),
                request.billingCycle().name(), request.billingLeadDays(), request.dueRule().name(),
                request.currency().toUpperCase(java.util.Locale.ROOT), operatorId, current.id(), current.version()
        );
        requireUpdated(updated);
        jdbcTemplate.update("DELETE FROM billing_plan_item WHERE billing_plan_id = ?", current.id());
        insertItems(current.id(), items, operatorId);
    }

    public void activate(BillingPlanSnapshot current, Long operatorId) {
        int updated = jdbcTemplate.update(
                """
                UPDATE billing_plan
                SET status = 'ACTIVE', activated_at = CURRENT_TIMESTAMP, activated_by = ?,
                    version = version + 1, updated_by = ?
                WHERE id = ? AND version = ? AND status = 'DRAFT' AND deleted = 0
                """,
                operatorId, operatorId, current.id(), current.version()
        );
        requireUpdated(updated);
    }

    public void terminate(BillingPlanSnapshot current, String reason, Long operatorId) {
        int updated = jdbcTemplate.update(
                """
                UPDATE billing_plan
                SET status = 'TERMINATED', terminated_at = CURRENT_TIMESTAMP,
                    terminated_by = ?, termination_reason = ?, version = version + 1, updated_by = ?
                WHERE id = ? AND version = ? AND status = 'ACTIVE' AND deleted = 0
                """,
                operatorId, reason, operatorId, current.id(), current.version()
        );
        requireUpdated(updated);
    }

    private void insertItems(Long billingPlanId, List<ResolvedBillingPlanItem> items, Long operatorId) {
        for (ResolvedBillingPlanItem item : items) {
            jdbcTemplate.update(
                    """
                    INSERT INTO billing_plan_item (
                        billing_plan_id, fee_rule_version_id, item_name, quantity,
                        unit_price, fixed_amount, amount, sort_order, created_by
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    billingPlanId, item.feeRuleVersionId(), item.itemName(), item.quantity(), item.unitPrice(),
                    item.fixedAmount(), item.amount(), item.sortOrder(), operatorId
            );
        }
    }

    private List<BillingPlanItemResponse> items(Long planId) {
        return jdbcTemplate.query(
                """
                SELECT item_record.id, item_record.fee_rule_version_id,
                       rule_record.rule_code, rule_record.rule_name,
                       version_record.calculation_type, item_record.item_name,
                       item_record.quantity, item_record.unit_price,
                       item_record.fixed_amount, item_record.amount, item_record.sort_order
                FROM billing_plan_item item_record
                JOIN fee_rule_version version_record ON version_record.id = item_record.fee_rule_version_id
                JOIN fee_rule rule_record ON rule_record.id = version_record.fee_rule_id
                WHERE item_record.billing_plan_id = ?
                ORDER BY item_record.sort_order, item_record.id
                """,
                (rs, rowNum) -> new BillingPlanItemResponse(
                        rs.getLong("id"),
                        rs.getLong("fee_rule_version_id"),
                        rs.getString("rule_code"),
                        rs.getString("rule_name"),
                        CalculationType.valueOf(rs.getString("calculation_type")),
                        rs.getString("item_name"),
                        rs.getBigDecimal("quantity"),
                        rs.getBigDecimal("unit_price"),
                        rs.getBigDecimal("fixed_amount"),
                        money(rs.getBigDecimal("amount")),
                        rs.getInt("sort_order")
                ),
                planId
        );
    }

    private Filter filter(String keyword, BillingPlanStatus status, Long tenantId, Long contractId, Long leaseId) {
        StringBuilder where = new StringBuilder(" WHERE plan_record.deleted = 0");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (plan_record.plan_no LIKE ? OR plan_record.plan_name LIKE ? OR tenant_record.name LIKE ? OR contract_record.code LIKE ?)");
            String pattern = "%" + keyword.trim() + "%";
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
        if (status != null) {
            where.append(" AND plan_record.status = ?");
            args.add(status.name());
        }
        if (tenantId != null) {
            where.append(" AND plan_record.tenant_id = ?");
            args.add(tenantId);
        }
        if (contractId != null) {
            where.append(" AND plan_record.contract_id = ?");
            args.add(contractId);
        }
        if (leaseId != null) {
            where.append(" AND plan_record.lease_id = ?");
            args.add(leaseId);
        }
        return new Filter(where.toString(), args);
    }

    private RowMapper<BillingPlanResponse> responseMapper(boolean includeItems) {
        return (rs, rowNum) -> {
            Long id = rs.getLong("id");
            return new BillingPlanResponse(
                    id,
                    rs.getString("plan_no"),
                    rs.getString("plan_name"),
                    rs.getLong("tenant_id"),
                    rs.getString("tenant_name"),
                    rs.getLong("contract_id"),
                    rs.getString("contract_no"),
                    rs.getObject("lease_id", Long.class),
                    rs.getString("lease_no"),
                    rs.getObject("effective_from", LocalDate.class),
                    rs.getObject("effective_to", LocalDate.class),
                    BillingCycle.valueOf(rs.getString("billing_cycle")),
                    rs.getInt("billing_lead_days"),
                    DueRule.valueOf(rs.getString("due_rule")),
                    rs.getString("currency"),
                    BillingPlanStatus.valueOf(rs.getString("status")),
                    rs.getLong("item_count"),
                    money(rs.getBigDecimal("estimated_period_amount")),
                    rs.getLong("version"),
                    timestamp(rs.getTimestamp("created_at")),
                    timestamp(rs.getTimestamp("updated_at")),
                    List.of()
            );
        };
    }

    private Long generatedId(KeyHolder keyHolder) {
        Number key = keyHolder.getKey();
        if (key == null) throw new BusinessException(500, "计费计划创建失败");
        return key.longValue();
    }

    private void requireUpdated(int updated) {
        if (updated != 1) throw new BusinessException(409, "计费计划已被其他操作更新，请刷新后重试");
    }

    private BigDecimal money(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDateTime timestamp(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private record Filter(String where, List<Object> args) {
    }

    public record BillingPlanSnapshot(Long id, String planNo, BillingPlanStatus status, long version) {
    }

    public record ContractLeaseBounds(
            Long tenantId,
            LocalDate contractStart,
            LocalDate contractEnd,
            Long leaseId,
            LocalDate leaseStart,
            LocalDate leaseEnd,
            String leaseStatus
    ) {
    }

    public record ResolvedBillingPlanItem(
            Long feeRuleVersionId,
            String itemName,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal fixedAmount,
            BigDecimal amount,
            int sortOrder
    ) {
    }
}
