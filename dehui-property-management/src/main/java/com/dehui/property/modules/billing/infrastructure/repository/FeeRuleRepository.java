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
import com.dehui.property.modules.billing.domain.model.CalculationType;
import com.dehui.property.modules.billing.domain.model.FeeRuleStatus;
import com.dehui.property.modules.billing.domain.model.FeeRuleVersionStatus;
import com.dehui.property.modules.billing.domain.model.FeeType;
import com.dehui.property.modules.billing.dto.FeeRuleCreateRequest;
import com.dehui.property.modules.billing.dto.FeeRuleResponse;
import com.dehui.property.modules.billing.dto.FeeRuleSummaryResponse;
import com.dehui.property.modules.billing.dto.FeeRuleUpdateRequest;
import com.dehui.property.modules.billing.dto.FeeRuleVersionResponse;
import com.dehui.property.modules.billing.domain.service.FeeRuleRevisionPolicy;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class FeeRuleRepository {

    private static final String SELECT_CURRENT = """
            SELECT fr.id, fr.rule_code, fr.rule_name, fr.fee_type, fr.status,
                   fr.current_version_id, fr.current_version_no,
                   fr.active_version_id, fr.active_version_no,
                   version_record.calculation_type, version_record.billing_cycle,
                   version_record.effective_from, version_record.effective_to,
                   version_record.fixed_amount, version_record.unit_price, version_record.currency,
                   fr.description, fr.version, fr.created_at, fr.updated_at
            FROM fee_rule fr
            JOIN fee_rule_version version_record ON version_record.id = fr.current_version_id
            """;

    private static final String SELECT_ACTIVE = """
            SELECT fr.id, fr.rule_code, fr.rule_name, fr.fee_type, fr.status,
                   fr.current_version_id, fr.current_version_no,
                   fr.active_version_id, fr.active_version_no,
                   version_record.calculation_type, version_record.billing_cycle,
                   version_record.effective_from, version_record.effective_to,
                   version_record.fixed_amount, version_record.unit_price, version_record.currency,
                   fr.description, fr.version, fr.created_at, fr.updated_at
            FROM fee_rule fr
            JOIN fee_rule_version version_record ON version_record.id = fr.active_version_id
            """;

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "ruleCode", "fr.rule_code",
            "ruleName", "fr.rule_name",
            "feeType", "fr.fee_type",
            "status", "fr.status",
            "effectiveFrom", "version_record.effective_from",
            "updatedAt", "fr.updated_at"
    );

    private final JdbcTemplate jdbcTemplate;
    private final CustomFieldQueryService customFieldQueryService;
    private final CustomFieldProjectionService customFieldProjectionService;

    public FeeRuleRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null, null);
    }

    public FeeRuleRepository(JdbcTemplate jdbcTemplate, CustomFieldQueryService customFieldQueryService) {
        this(jdbcTemplate, customFieldQueryService, null);
    }

    @Autowired
    public FeeRuleRepository(
            JdbcTemplate jdbcTemplate,
            CustomFieldQueryService customFieldQueryService,
            CustomFieldProjectionService customFieldProjectionService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.customFieldQueryService = customFieldQueryService;
        this.customFieldProjectionService = customFieldProjectionService;
    }

    public PageResponse<FeeRuleResponse> search(
            String keyword,
            FeeRuleStatus status,
            FeeType feeType,
            CalculationType calculationType,
            String sortField,
            String sortDirection,
            Integer page,
            Integer pageSize
    ) {
        return search(
                keyword, status, feeType, calculationType,
                sortField, sortDirection, null, null, null, page, pageSize
        );
    }

    public PageResponse<FeeRuleResponse> search(
            String keyword,
            FeeRuleStatus status,
            FeeType feeType,
            CalculationType calculationType,
            String sortField,
            String sortDirection,
            String sorts,
            String groupBy,
            String customFilters,
            Integer page,
            Integer pageSize
    ) {
        Filter filter = filter(keyword, status, feeType, calculationType);
        boolean activeTerms = status == FeeRuleStatus.ACTIVE;
        String selectedVersionColumn = activeTerms ? "fr.active_version_id" : "fr.current_version_id";
        CompiledQuery customQuery = compileCustomQuery(
                customFilters,
                groupBy,
                sorts,
                NativeOrderPolicy.of(SORT_FIELDS, sortField, sortDirection, "fr.updated_at", "fr.id")
        );
        String where = filter.where();
        List<Object> args = new ArrayList<>(filter.args());
        if (customQuery != null && customQuery.hasPredicates()) {
            where += " AND " + customQuery.predicateSql();
            args.addAll(customQuery.predicateArguments());
        }
        String orderBy = customQuery == null || !customQuery.hasOrderings()
                ? WorkspaceOrderBy.build(SORT_FIELDS, groupBy, sorts, sortField, sortDirection, "fr.updated_at", "fr.id")
                : String.join(", ", customQuery.orderExpressions());
        PageResponse<FeeRuleResponse> pageResponse = JdbcPagination.query(
                jdbcTemplate,
                (activeTerms ? SELECT_ACTIVE : SELECT_CURRENT) + where
                        + " ORDER BY " + orderBy,
                "SELECT COUNT(*) FROM fee_rule fr JOIN fee_rule_version version_record "
                        + "ON version_record.id = " + selectedVersionColumn + " " + where,
                args,
                customQuery == null ? List.of() : customQuery.orderArguments(),
                page,
                pageSize,
                responseMapper()
        );
        return customFieldProjectionService == null
                ? pageResponse
                : customFieldProjectionService.attach(
                        "fee-rules", pageResponse, FeeRuleResponse::id, FeeRuleResponse::withCustomFields
                );
    }

    public PageResponse<FeeRuleResponse> search(
            String keyword,
            FeeRuleStatus status,
            FeeType feeType,
            CalculationType calculationType,
            String sortField,
            String sortDirection,
            String sorts,
            String groupBy,
            Integer page,
            Integer pageSize
    ) {
        return search(
                keyword, status, feeType, calculationType, sortField, sortDirection,
                sorts, groupBy, null, page, pageSize
        );
    }

    public FeeRuleSummaryResponse summary(
            String keyword,
            FeeRuleStatus status,
            FeeType feeType,
            CalculationType calculationType
    ) {
        return summary(keyword, status, feeType, calculationType, null);
    }

    public FeeRuleSummaryResponse summary(
            String keyword,
            FeeRuleStatus status,
            FeeType feeType,
            CalculationType calculationType,
            String customFilters
    ) {
        Filter filter = filter(keyword, status, feeType, calculationType);
        String where = filter.where();
        List<Object> args = new ArrayList<>(filter.args());
        CompiledQuery customQuery = compileCustomQuery(customFilters, null, null, null);
        if (customQuery != null && customQuery.hasPredicates()) {
            where += " AND " + customQuery.predicateSql();
            args.addAll(customQuery.predicateArguments());
        }
        String selectedVersionColumn = status == FeeRuleStatus.ACTIVE
                ? "fr.active_version_id"
                : "fr.current_version_id";
        String summarySql = """
                SELECT COUNT(*) AS total_count,
                       COALESCE(SUM(CASE WHEN fr.status = 'DRAFT' THEN 1 ELSE 0 END), 0) AS draft_count,
                       COALESCE(SUM(CASE WHEN fr.status = 'ACTIVE' THEN 1 ELSE 0 END), 0) AS active_count,
                       COALESCE(SUM(CASE WHEN fr.status = 'DISABLED' THEN 1 ELSE 0 END), 0) AS disabled_count
                FROM fee_rule fr
                JOIN fee_rule_version version_record ON version_record.id = %s
                """.formatted(selectedVersionColumn) + where;
        return jdbcTemplate.queryForObject(
                summarySql,
                (rs, rowNum) -> new FeeRuleSummaryResponse(
                        rs.getLong("total_count"),
                        rs.getLong("draft_count"),
                        rs.getLong("active_count"),
                        rs.getLong("disabled_count")
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
                "fee-rules",
                customFilters,
                groupBy,
                sorts,
                orderPolicy
        );
    }

    public FeeRuleResponse find(Long id) {
        List<FeeRuleResponse> rows = jdbcTemplate.query(
                SELECT_CURRENT + " WHERE fr.id = ? AND fr.deleted = 0",
                responseMapper(),
                id
        );
        if (rows.isEmpty()) throw new BusinessException(404, "收费规则不存在");
        return rows.getFirst();
    }

    public List<FeeRuleVersionResponse> versions(Long feeRuleId) {
        ensureExists(feeRuleId);
        return jdbcTemplate.query(
                """
                SELECT id, fee_rule_id, version_no, status, calculation_type, billing_cycle,
                       effective_from, effective_to, fixed_amount, unit_price, currency,
                       activated_at, created_at
                FROM fee_rule_version
                WHERE fee_rule_id = ?
                ORDER BY version_no DESC
                """,
                versionResponseMapper(),
                feeRuleId
        );
    }

    public Long create(FeeRuleCreateRequest request, Long operatorId) {
        KeyHolder aggregateKey = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        """
                        INSERT INTO fee_rule (
                            rule_code, rule_name, fee_type, status, current_version_no,
                            version, description, created_by, updated_by
                        ) VALUES (?, ?, ?, 'DRAFT', 1, 0, ?, ?, ?)
                        """,
                        Statement.RETURN_GENERATED_KEYS
                );
                statement.setString(1, ruleCode(request.ruleCode()));
                statement.setString(2, request.ruleName().trim());
                statement.setString(3, request.feeType().name());
                statement.setString(4, request.description());
                statement.setLong(5, operatorId);
                statement.setLong(6, operatorId);
                return statement;
            }, aggregateKey);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "收费规则编号已存在");
        }
        Long id = generatedId(aggregateKey, "收费规则创建失败");
        Long versionId = insertVersion(id, 1, request.calculationType(), request.billingCycle(),
                request.effectiveFrom(), request.effectiveTo(), request.fixedAmount(), request.unitPrice(),
                request.currency(), operatorId);
        jdbcTemplate.update("UPDATE fee_rule SET current_version_id = ? WHERE id = ?", versionId, id);
        return id;
    }

    public FeeRuleSnapshot lock(Long id) {
        List<FeeRuleSnapshot> rows = jdbcTemplate.query(
                """
                SELECT id, rule_code, status, current_version_id, current_version_no,
                       active_version_id, active_version_no, version
                FROM fee_rule
                WHERE id = ? AND deleted = 0
                FOR UPDATE
                """,
                (rs, rowNum) -> new FeeRuleSnapshot(
                        rs.getLong("id"),
                        rs.getString("rule_code"),
                        FeeRuleStatus.valueOf(rs.getString("status")),
                        rs.getObject("current_version_id", Long.class),
                        rs.getInt("current_version_no"),
                        rs.getObject("active_version_id", Long.class),
                        rs.getObject("active_version_no", Integer.class),
                        rs.getLong("version")
                ),
                id
        );
        if (rows.isEmpty()) throw new BusinessException(404, "收费规则不存在");
        return rows.getFirst();
    }

    public FeeRuleVersionSnapshot versionSnapshot(Long versionId) {
        List<FeeRuleVersionSnapshot> rows = jdbcTemplate.query(
                """
                SELECT version_record.id, version_record.fee_rule_id, version_record.version_no,
                       version_record.status, version_record.calculation_type,
                       version_record.billing_cycle, version_record.effective_from,
                       version_record.effective_to, version_record.fixed_amount,
                       version_record.unit_price, version_record.currency,
                       aggregate_record.rule_code, aggregate_record.rule_name, aggregate_record.fee_type,
                       aggregate_record.status AS rule_status, aggregate_record.active_version_id
                FROM fee_rule_version version_record
                JOIN fee_rule aggregate_record ON aggregate_record.id = version_record.fee_rule_id
                WHERE version_record.id = ? AND aggregate_record.deleted = 0
                """,
                (rs, rowNum) -> new FeeRuleVersionSnapshot(
                        rs.getLong("id"),
                        rs.getLong("fee_rule_id"),
                        rs.getInt("version_no"),
                        FeeRuleVersionStatus.valueOf(rs.getString("status")),
                        CalculationType.valueOf(rs.getString("calculation_type")),
                        BillingCycle.valueOf(rs.getString("billing_cycle")),
                        rs.getObject("effective_from", LocalDate.class),
                        rs.getObject("effective_to", LocalDate.class),
                        rs.getBigDecimal("fixed_amount"),
                        rs.getBigDecimal("unit_price"),
                        rs.getString("currency"),
                        rs.getString("rule_code"),
                        rs.getString("rule_name"),
                        FeeType.valueOf(rs.getString("fee_type")),
                        FeeRuleStatus.valueOf(rs.getString("rule_status")),
                        rs.getObject("active_version_id", Long.class)
                ),
                versionId
        );
        if (rows.isEmpty()) throw new BusinessException(404, "收费规则版本不存在");
        return rows.getFirst();
    }

    public Long addRevision(FeeRuleSnapshot current, FeeRuleUpdateRequest request, Long operatorId) {
        int nextVersionNo = FeeRuleRevisionPolicy.nextVersionNo(current.currentVersionNo());
        jdbcTemplate.update(
                "UPDATE fee_rule_version SET status = 'RETIRED' WHERE id = ? AND status = 'DRAFT'",
                current.currentVersionId()
        );
        Long versionId = insertVersion(current.id(), nextVersionNo, request.calculationType(),
                request.billingCycle(), request.effectiveFrom(), request.effectiveTo(), request.fixedAmount(),
                request.unitPrice(), request.currency(), operatorId);
        int updated = jdbcTemplate.update(
                """
                UPDATE fee_rule
                SET rule_name = ?, fee_type = ?, current_version_id = ?, current_version_no = ?,
                    description = ?, version = version + 1, updated_by = ?
                WHERE id = ? AND version = ? AND deleted = 0
                """,
                request.ruleName().trim(), request.feeType().name(), versionId, nextVersionNo,
                request.description(), operatorId, current.id(), current.version()
        );
        requireUpdated(updated);
        return versionId;
    }

    public void activate(FeeRuleSnapshot current, Long operatorId) {
        if (current.activeVersionId() != null && !current.activeVersionId().equals(current.currentVersionId())) {
            jdbcTemplate.update(
                    "UPDATE fee_rule_version SET status = 'RETIRED' WHERE id = ? AND status = 'ACTIVE'",
                    current.activeVersionId()
            );
        }
        int activated = jdbcTemplate.update(
                """
                UPDATE fee_rule_version
                SET status = 'ACTIVE', activated_at = CURRENT_TIMESTAMP, activated_by = ?
                WHERE id = ? AND fee_rule_id = ? AND status = 'DRAFT'
                """,
                operatorId, current.currentVersionId(), current.id()
        );
        if (activated != 1) throw new BusinessException(409, "最新规则版本不是可生效草稿");
        int updated = jdbcTemplate.update(
                """
                UPDATE fee_rule
                SET status = 'ACTIVE', active_version_id = current_version_id,
                    active_version_no = current_version_no, version = version + 1, updated_by = ?
                WHERE id = ? AND version = ? AND deleted = 0
                """,
                operatorId, current.id(), current.version()
        );
        requireUpdated(updated);
    }

    public void disable(FeeRuleSnapshot current, Long operatorId) {
        int updated = jdbcTemplate.update(
                """
                UPDATE fee_rule
                SET status = 'DISABLED', version = version + 1, updated_by = ?
                WHERE id = ? AND version = ? AND deleted = 0
                """,
                operatorId, current.id(), current.version()
        );
        requireUpdated(updated);
    }

    private Long insertVersion(
            Long feeRuleId,
            int versionNo,
            CalculationType calculationType,
            BillingCycle billingCycle,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            java.math.BigDecimal fixedAmount,
            java.math.BigDecimal unitPrice,
            String currency,
            Long operatorId
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO fee_rule_version (
                        fee_rule_id, version_no, calculation_type, billing_cycle,
                        effective_from, effective_to, fixed_amount, unit_price,
                        currency, status, created_by
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, feeRuleId);
            statement.setInt(2, versionNo);
            statement.setString(3, calculationType.name());
            statement.setString(4, billingCycle.name());
            statement.setDate(5, Date.valueOf(effectiveFrom));
            statement.setDate(6, effectiveTo == null ? null : Date.valueOf(effectiveTo));
            statement.setBigDecimal(7, fixedAmount);
            statement.setBigDecimal(8, unitPrice);
            statement.setString(9, currency.toUpperCase(Locale.ROOT));
            statement.setLong(10, operatorId);
            return statement;
        }, keyHolder);
        return generatedId(keyHolder, "收费规则版本创建失败");
    }

    private Filter filter(String keyword, FeeRuleStatus status, FeeType feeType, CalculationType calculationType) {
        StringBuilder where = new StringBuilder(" WHERE fr.deleted = 0");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (fr.rule_code LIKE ? OR fr.rule_name LIKE ? OR fr.description LIKE ?)");
            String pattern = "%" + keyword.trim() + "%";
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
        if (status != null) {
            where.append(" AND fr.status = ?");
            args.add(status.name());
        }
        if (feeType != null) {
            where.append(" AND fr.fee_type = ?");
            args.add(feeType.name());
        }
        if (calculationType != null) {
            where.append(" AND version_record.calculation_type = ?");
            args.add(calculationType.name());
        }
        return new Filter(where.toString(), args);
    }

    private RowMapper<FeeRuleResponse> responseMapper() {
        return (rs, rowNum) -> new FeeRuleResponse(
                rs.getLong("id"),
                rs.getString("rule_code"),
                rs.getString("rule_name"),
                FeeType.valueOf(rs.getString("fee_type")),
                FeeRuleStatus.valueOf(rs.getString("status")),
                rs.getObject("current_version_id", Long.class),
                rs.getInt("current_version_no"),
                rs.getObject("active_version_id", Long.class),
                rs.getObject("active_version_no", Integer.class),
                CalculationType.valueOf(rs.getString("calculation_type")),
                BillingCycle.valueOf(rs.getString("billing_cycle")),
                rs.getObject("effective_from", LocalDate.class),
                rs.getObject("effective_to", LocalDate.class),
                rs.getBigDecimal("fixed_amount"),
                rs.getBigDecimal("unit_price"),
                rs.getString("currency"),
                rs.getString("description"),
                rs.getLong("version"),
                timestamp(rs.getTimestamp("created_at")),
                timestamp(rs.getTimestamp("updated_at"))
        );
    }

    private RowMapper<FeeRuleVersionResponse> versionResponseMapper() {
        return (rs, rowNum) -> new FeeRuleVersionResponse(
                rs.getLong("id"),
                rs.getLong("fee_rule_id"),
                rs.getInt("version_no"),
                FeeRuleVersionStatus.valueOf(rs.getString("status")),
                CalculationType.valueOf(rs.getString("calculation_type")),
                BillingCycle.valueOf(rs.getString("billing_cycle")),
                rs.getObject("effective_from", LocalDate.class),
                rs.getObject("effective_to", LocalDate.class),
                rs.getBigDecimal("fixed_amount"),
                rs.getBigDecimal("unit_price"),
                rs.getString("currency"),
                timestamp(rs.getTimestamp("activated_at")),
                timestamp(rs.getTimestamp("created_at"))
        );
    }

    private void ensureExists(Long id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fee_rule WHERE id = ? AND deleted = 0", Integer.class, id
        );
        if (count == null || count == 0) throw new BusinessException(404, "收费规则不存在");
    }

    private String ruleCode(String requested) {
        if (requested != null && !requested.isBlank()) return requested.trim().toUpperCase(Locale.ROOT);
        return "FR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private Long generatedId(KeyHolder keyHolder, String message) {
        Number key = keyHolder.getKey();
        if (key == null) throw new BusinessException(500, message);
        return key.longValue();
    }

    private void requireUpdated(int updated) {
        if (updated != 1) throw new BusinessException(409, "收费规则已被其他操作更新，请刷新后重试");
    }

    private LocalDateTime timestamp(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private record Filter(String where, List<Object> args) {
    }

    public record FeeRuleSnapshot(
            Long id,
            String ruleCode,
            FeeRuleStatus status,
            Long currentVersionId,
            int currentVersionNo,
            Long activeVersionId,
            Integer activeVersionNo,
            long version
    ) {
    }

    public record FeeRuleVersionSnapshot(
            Long id,
            Long feeRuleId,
            int versionNo,
            FeeRuleVersionStatus status,
            CalculationType calculationType,
            BillingCycle billingCycle,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            java.math.BigDecimal fixedAmount,
            java.math.BigDecimal unitPrice,
            String currency,
            String ruleCode,
            String ruleName,
            FeeType feeType,
            FeeRuleStatus ruleStatus,
            Long activeVersionId
    ) {
    }
}
