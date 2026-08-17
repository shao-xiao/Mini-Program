package com.dehui.property.modules.contract.infrastructure.repository;

import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcPagination;
import com.dehui.property.common.PageResponse;
import com.dehui.property.modules.contract.domain.model.ContractStatus;
import com.dehui.property.modules.contract.dto.ContractCreateRequest;
import com.dehui.property.modules.contract.dto.ContractHistoryResponse;
import com.dehui.property.modules.contract.dto.ContractResponse;
import com.dehui.property.modules.contract.dto.ContractSummaryResponse;
import com.dehui.property.modules.contract.dto.ContractUpdateRequest;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ContractRepository {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "contractNo", "c.code",
            "tenantName", "t.name",
            "startDate", "c.start_date",
            "endDate", "c.end_date",
            "status", "c.status",
            "updatedAt", "c.updated_at"
    );

    private static final String SELECT = """
            SELECT c.id,
                   c.code AS contract_no,
                   c.tenant_id,
                   t.name AS tenant_name,
                   c.status,
                   c.effective_date,
                   c.start_date,
                   c.end_date,
                   c.signed_at,
                   c.currency,
                   c.current_revision_no,
                   c.renewal_of_contract_id,
                   c.version,
                   c.rent_amount,
                   c.deposit_amount,
                   c.remark,
                   c.created_at,
                   c.updated_at,
                   (SELECT GROUP_CONCAT(cr.room_id ORDER BY cr.id SEPARATOR ',')
                      FROM contract_room cr
                     WHERE cr.contract_id = c.id AND cr.deleted = 0) AS room_ids,
                   (SELECT GROUP_CONCAT(r.room_no ORDER BY cr.id SEPARATOR ',')
                      FROM contract_room cr
                      JOIN building_room r ON r.id = cr.room_id AND r.deleted = 0
                     WHERE cr.contract_id = c.id AND cr.deleted = 0) AS room_names
            FROM contract c
            JOIN tenant t ON t.id = c.tenant_id AND t.deleted = 0
            """;

    private final JdbcTemplate jdbcTemplate;

    public ContractRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResponse<ContractResponse> search(
            String keyword,
            String status,
            Long tenantId,
            LocalDate endDateFrom,
            LocalDate endDateTo,
            String sortField,
            String sortDirection,
            Integer page,
            Integer pageSize
    ) {
        List<Object> args = new ArrayList<>();
        String where = where(keyword, status, tenantId, endDateFrom, endDateTo, args);
        String orderBy = SORT_FIELDS.getOrDefault(sortField, "c.updated_at")
                + ("asc".equalsIgnoreCase(sortDirection) ? " ASC" : " DESC")
                + ", c.id DESC";
        return JdbcPagination.query(
                jdbcTemplate,
                SELECT + where + " ORDER BY " + orderBy,
                "SELECT COUNT(*) FROM contract c JOIN tenant t ON t.id = c.tenant_id AND t.deleted = 0 " + where,
                args,
                page,
                pageSize,
                contractRowMapper()
        );
    }

    public ContractSummaryResponse summary(String keyword, String status, Long tenantId) {
        List<Object> args = new ArrayList<>();
        String where = where(keyword, status, tenantId, null, null, args);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT COUNT(*) AS total,
                       SUM(CASE WHEN c.status = 'DRAFT' THEN 1 ELSE 0 END) AS draft_count,
                       SUM(CASE WHEN c.status IN ('PENDING_APPROVAL', 'PENDING') THEN 1 ELSE 0 END) AS pending_count,
                       SUM(CASE WHEN c.status = 'ACTIVE' THEN 1 ELSE 0 END) AS active_count,
                       SUM(CASE WHEN c.status = 'SUSPENDED' THEN 1 ELSE 0 END) AS suspended_count,
                       SUM(CASE WHEN c.status = 'ACTIVE'
                                 AND c.end_date BETWEEN CURRENT_DATE AND DATE_ADD(CURRENT_DATE, INTERVAL 30 DAY)
                                THEN 1 ELSE 0 END) AS expiring_count,
                       SUM(CASE WHEN c.status = 'TERMINATED' THEN 1 ELSE 0 END) AS terminated_count,
                       SUM(CASE WHEN c.status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_count
                FROM contract c
                JOIN tenant t ON t.id = c.tenant_id AND t.deleted = 0
                """ + where,
                args.toArray()
        );
        return new ContractSummaryResponse(
                number(row, "total"),
                number(row, "draft_count"),
                number(row, "pending_count"),
                number(row, "active_count"),
                number(row, "suspended_count"),
                number(row, "expiring_count"),
                number(row, "terminated_count"),
                number(row, "cancelled_count")
        );
    }

    public List<ContractHistoryResponse> history(Long contractId) {
        find(contractId);
        return jdbcTemplate.query(
                """
                SELECT history.source_type,
                       history.source_id,
                       history.lease_id,
                       history.lease_no,
                       history.from_status,
                       history.to_status,
                       history.reason,
                       history.operator_id,
                       u.username AS operator_name,
                       history.occurred_at
                FROM (
                    SELECT 'CONTRACT' AS source_type,
                           h.id AS source_id,
                           NULL AS lease_id,
                           NULL AS lease_no,
                           h.from_status,
                           h.to_status,
                           h.reason,
                           h.operator_id,
                           h.occurred_at
                    FROM contract_status_history h
                    WHERE h.contract_id = ?
                    UNION ALL
                    SELECT 'LEASE' AS source_type,
                           h.id AS source_id,
                           l.id AS lease_id,
                           l.lease_no,
                           h.from_status,
                           h.to_status,
                           h.reason,
                           h.operator_id,
                           h.occurred_at
                    FROM lease_status_history h
                    JOIN lease l ON l.id = h.lease_id AND l.deleted = 0
                    WHERE l.contract_id = ?
                ) history
                LEFT JOIN sys_user u ON u.id = history.operator_id AND u.deleted = 0
                ORDER BY history.occurred_at DESC, history.source_type, history.source_id DESC
                """,
                (rs, rowNum) -> new ContractHistoryResponse(
                        rs.getString("source_type"),
                        rs.getLong("source_id"),
                        nullableLong(rs, "lease_id"),
                        rs.getString("lease_no"),
                        rs.getString("from_status"),
                        rs.getString("to_status"),
                        rs.getString("reason"),
                        rs.getLong("operator_id"),
                        rs.getString("operator_name"),
                        dateTime(rs, "occurred_at")
                ),
                contractId,
                contractId
        );
    }

    public ContractResponse find(Long id) {
        List<ContractResponse> rows = jdbcTemplate.query(
                SELECT + " WHERE c.id = ? AND c.deleted = 0",
                contractRowMapper(),
                id
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "合同不存在");
        }
        return rows.getFirst();
    }

    public ContractSnapshot lock(Long id) {
        List<ContractSnapshot> rows = jdbcTemplate.query(
                """
                SELECT id, code, tenant_id, status, start_date, end_date, currency,
                       current_revision_no, version, rent_amount, deposit_amount
                FROM contract
                WHERE id = ? AND deleted = 0
                FOR UPDATE
                """,
                (rs, rowNum) -> new ContractSnapshot(
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getLong("tenant_id"),
                        ContractStatus.fromDatabase(rs.getString("status")),
                        rs.getObject("start_date", LocalDate.class),
                        rs.getObject("end_date", LocalDate.class),
                        rs.getString("currency"),
                        rs.getInt("current_revision_no"),
                        rs.getLong("version"),
                        rs.getBigDecimal("rent_amount"),
                        rs.getBigDecimal("deposit_amount")
                ),
                id
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "合同不存在");
        }
        return rows.getFirst();
    }

    public Long create(ContractCreateRequest request, Long operatorId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        """
                        INSERT INTO contract (
                            code, tenant_id, room_id, start_date, end_date, effective_date,
                            signed_at, currency, current_revision_no, renewal_of_contract_id,
                            rent_amount, deposit_amount, status, version,
                            created_by, updated_by, remark
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, 'DRAFT', 0, ?, ?, ?)
                        """,
                        Statement.RETURN_GENERATED_KEYS
                );
                statement.setString(1, contractNo(request.contractNo()));
                statement.setLong(2, request.tenantId());
                statement.setLong(3, request.roomIds().getFirst());
                statement.setDate(4, Date.valueOf(request.startDate()));
                statement.setDate(5, Date.valueOf(request.endDate()));
                statement.setDate(6, Date.valueOf(request.effectiveDate() == null ? request.startDate() : request.effectiveDate()));
                statement.setTimestamp(7, request.signedAt() == null ? null : Timestamp.valueOf(request.signedAt()));
                statement.setString(8, currency(request.currency()));
                if (request.renewalOfContractId() == null) statement.setObject(9, null); else statement.setLong(9, request.renewalOfContractId());
                statement.setBigDecimal(10, zero(request.rentAmount()));
                statement.setBigDecimal(11, zero(request.depositAmount()));
                statement.setLong(12, operatorId);
                statement.setLong(13, operatorId);
                statement.setString(14, request.remark());
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "合同编号已存在");
        }
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BusinessException(500, "合同创建失败");
        }
        return key.longValue();
    }

    public long update(ContractSnapshot current, ContractUpdateRequest request, Long operatorId) {
        int nextRevision = current.currentRevisionNo() + 1;
        int updated;
        try {
            updated = jdbcTemplate.update(
                    """
                    UPDATE contract
                    SET code = ?, tenant_id = ?, room_id = ?, start_date = ?, end_date = ?,
                        effective_date = ?, signed_at = ?, currency = ?, rent_amount = ?,
                        deposit_amount = ?, current_revision_no = ?, version = version + 1,
                        updated_by = ?, remark = ?
                    WHERE id = ? AND version = ? AND deleted = 0
                    """,
                    request.contractNo().trim(),
                    request.tenantId(),
                    request.roomIds().getFirst(),
                    request.startDate(),
                    request.endDate(),
                    request.effectiveDate() == null ? request.startDate() : request.effectiveDate(),
                    request.signedAt(),
                    currency(request.currency()),
                    zero(request.rentAmount()),
                    zero(request.depositAmount()),
                    nextRevision,
                    operatorId,
                    request.remark(),
                    current.id(),
                    current.version()
            );
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "合同编号已存在");
        }
        requireOptimisticUpdate(updated);
        return current.version() + 1;
    }

    public long transition(Long id, ContractStatus from, ContractStatus to, long version, Long operatorId) {
        int updated;
        if (from == ContractStatus.PENDING_APPROVAL) {
            updated = jdbcTemplate.update(
                    """
                    UPDATE contract
                    SET status = ?, version = version + 1, updated_by = ?
                    WHERE id = ? AND status IN ('PENDING_APPROVAL', 'PENDING')
                      AND version = ? AND deleted = 0
                    """,
                    to.name(), operatorId, id, version
            );
        } else {
            updated = jdbcTemplate.update(
                    """
                    UPDATE contract
                    SET status = ?, version = version + 1, updated_by = ?
                    WHERE id = ? AND status = ? AND version = ? AND deleted = 0
                    """,
                    to.name(), operatorId, id, from.name(), version
            );
        }
        requireOptimisticUpdate(updated);
        return version + 1;
    }

    public long extendForRenewal(
            ContractSnapshot current,
            LocalDate effectiveFrom,
            LocalDate newEndDate,
            String reason,
            Long operatorId
    ) {
        if (!newEndDate.isAfter(current.endDate())) {
            throw new BusinessException(400, "续租结束日期必须晚于当前合同结束日期");
        }
        int nextRevision = current.currentRevisionNo() + 1;
        int updated = jdbcTemplate.update(
                """
                UPDATE contract
                SET end_date = ?, current_revision_no = ?, version = version + 1, updated_by = ?
                WHERE id = ? AND version = ? AND deleted = 0
                """,
                newEndDate, nextRevision, operatorId, current.id(), current.version()
        );
        requireOptimisticUpdate(updated);
        addRevision(
                current.id(), nextRevision, current.rentAmount(), current.depositAmount(), current.currency(),
                effectiveFrom, reason == null ? "租约续期" : reason, operatorId
        );
        return current.version() + 1;
    }

    public long countLiveLeases(Long contractId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM lease
                WHERE contract_id = ? AND deleted = 0
                  AND status IN ('PENDING_CHECKIN', 'PARTIALLY_ACTIVE', 'ACTIVE',
                                 'RENEWING', 'CHECKOUT_PENDING', 'PARTIALLY_TERMINATED')
                """,
                Long.class,
                contractId
        );
        return count == null ? 0 : count;
    }

    public void ensureTenantAndRooms(Long tenantId, List<Long> roomIds) {
        Integer tenantCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tenant WHERE id = ? AND deleted = 0",
                Integer.class,
                tenantId
        );
        if (tenantCount == null || tenantCount != 1) {
            throw new BusinessException(400, "租户不存在");
        }
        Set<Long> uniqueRoomIds = new LinkedHashSet<>(roomIds);
        if (uniqueRoomIds.size() != roomIds.size()) {
            throw new BusinessException(400, "房间列表包含重复项");
        }
        String placeholders = uniqueRoomIds.stream().map(ignored -> "?").collect(Collectors.joining(","));
        Integer roomCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM building_room WHERE deleted = 0 AND id IN (" + placeholders + ")",
                Integer.class,
                uniqueRoomIds.toArray()
        );
        if (roomCount == null || roomCount != uniqueRoomIds.size()) {
            throw new BusinessException(400, "存在无效房间");
        }
    }

    public void replaceRooms(Long contractId, List<Long> roomIds, Long operatorId) {
        jdbcTemplate.update(
                "UPDATE contract_room SET deleted = 1, updated_by = ? WHERE contract_id = ? AND deleted = 0",
                operatorId,
                contractId
        );
        for (Long roomId : new LinkedHashSet<>(roomIds)) {
            jdbcTemplate.update(
                    "INSERT INTO contract_room (contract_id, room_id, created_by, updated_by) VALUES (?, ?, ?, ?)",
                    contractId, roomId, operatorId, operatorId
            );
        }
    }

    public void addRevision(
            Long contractId,
            int revisionNo,
            java.math.BigDecimal rentAmount,
            java.math.BigDecimal depositAmount,
            String currency,
            LocalDate effectiveFrom,
            String reason,
            Long operatorId
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO contract_revision (
                    contract_id, revision_no, rent_terms_snapshot, deposit_terms_snapshot,
                    billing_terms_snapshot, change_reason, effective_from, created_by, updated_by
                ) VALUES (?, ?, JSON_OBJECT('rentAmount', ?, 'currency', ?),
                          JSON_OBJECT('depositAmount', ?, 'currency', ?), JSON_OBJECT(), ?, ?, ?, ?)
                """,
                contractId, revisionNo, zero(rentAmount), currency(currency), zero(depositAmount), currency(currency),
                reason, effectiveFrom, operatorId, operatorId
        );
    }

    public void addStatusHistory(
            Long contractId,
            ContractStatus from,
            ContractStatus to,
            String reason,
            Long operatorId,
            long aggregateVersion,
            String requestId
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO contract_status_history (
                    contract_id, from_status, to_status, reason, operator_id, aggregate_version, request_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                contractId, from == null ? null : from.name(), to.name(), reason, operatorId, aggregateVersion, requestId
        );
    }

    private String where(
            String keyword,
            String status,
            Long tenantId,
            LocalDate endDateFrom,
            LocalDate endDateTo,
            List<Object> args
    ) {
        StringBuilder where = new StringBuilder(" WHERE c.deleted = 0");
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (c.code LIKE ? OR t.name LIKE ? OR c.remark LIKE ?)");
            String pattern = "%" + keyword.trim() + "%";
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
        if (status != null && !status.isBlank()) {
            try {
                ContractStatus.fromDatabase(status);
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(400, "合同状态筛选值无效");
            }
            if (ContractStatus.fromDatabase(status) == ContractStatus.PENDING_APPROVAL) {
                where.append(" AND c.status IN ('PENDING_APPROVAL', 'PENDING')");
            } else {
                where.append(" AND c.status = ?");
                args.add(ContractStatus.fromDatabase(status).name());
            }
        }
        if (tenantId != null) {
            where.append(" AND c.tenant_id = ?");
            args.add(tenantId);
        }
        if (endDateFrom != null) {
            where.append(" AND c.end_date >= ?");
            args.add(endDateFrom);
        }
        if (endDateTo != null) {
            where.append(" AND c.end_date <= ?");
            args.add(endDateTo);
        }
        return where.toString();
    }

    private RowMapper<ContractResponse> contractRowMapper() {
        return (rs, rowNum) -> new ContractResponse(
                rs.getLong("id"),
                rs.getString("contract_no"),
                rs.getLong("tenant_id"),
                rs.getString("tenant_name"),
                ContractStatus.fromDatabase(rs.getString("status")),
                date(rs, "effective_date"),
                date(rs, "start_date"),
                date(rs, "end_date"),
                dateTime(rs, "signed_at"),
                rs.getString("currency"),
                rs.getInt("current_revision_no"),
                nullableLong(rs, "renewal_of_contract_id"),
                rs.getLong("version"),
                longList(rs.getString("room_ids")),
                stringList(rs.getString("room_names")),
                rs.getBigDecimal("rent_amount"),
                rs.getBigDecimal("deposit_amount"),
                rs.getString("remark"),
                dateTime(rs, "created_at"),
                dateTime(rs, "updated_at")
        );
    }

    private LocalDate date(ResultSet rs, String column) throws SQLException {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private LocalDateTime dateTime(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private List<Long> longList(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(Long::valueOf).toList();
    }

    private List<String> stringList(String value) {
        if (value == null || value.isBlank()) return List.of();
        return List.of(value.split(","));
    }

    private long number(Map<String, Object> row, String name) {
        Object value = row.get(name);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private java.math.BigDecimal zero(java.math.BigDecimal value) {
        return value == null ? java.math.BigDecimal.ZERO : value;
    }

    private String currency(String value) {
        return value == null || value.isBlank() ? "CNY" : value.toUpperCase(Locale.ROOT);
    }

    private String contractNo(String value) {
        if (value != null && !value.isBlank()) return value.trim();
        return "CON-" + LocalDate.now().toString().replace("-", "") + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private void requireOptimisticUpdate(int updated) {
        if (updated == 0) {
            throw new BusinessException(409, "合同已被其他操作更新，请刷新后重试");
        }
    }

    public record ContractSnapshot(
            Long id,
            String contractNo,
            Long tenantId,
            ContractStatus status,
            LocalDate startDate,
            LocalDate endDate,
            String currency,
            int currentRevisionNo,
            long version,
            java.math.BigDecimal rentAmount,
            java.math.BigDecimal depositAmount
    ) {
    }
}
