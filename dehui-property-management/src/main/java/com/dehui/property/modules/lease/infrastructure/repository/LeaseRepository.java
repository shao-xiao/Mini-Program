package com.dehui.property.modules.lease.infrastructure.repository;

import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcPagination;
import com.dehui.property.common.PageResponse;
import com.dehui.property.common.WorkspaceOrderBy;
import com.dehui.property.modules.customfield.application.CustomFieldProjectionService;
import com.dehui.property.modules.customfield.application.CustomFieldQueryCompiler.CompiledQuery;
import com.dehui.property.modules.customfield.application.CustomFieldQueryCompiler.NativeOrderPolicy;
import com.dehui.property.modules.customfield.application.CustomFieldQueryService;
import com.dehui.property.modules.contract.domain.model.ContractStatus;
import com.dehui.property.modules.lease.domain.model.LeaseRoomStatus;
import com.dehui.property.modules.lease.domain.model.LeaseStatus;
import com.dehui.property.modules.lease.dto.LeaseCreateRequest;
import com.dehui.property.modules.lease.dto.LeaseResponse;
import com.dehui.property.modules.lease.dto.LeaseSummaryResponse;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class LeaseRepository {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "leaseNo", "l.lease_no",
            "tenantName", "t.name",
            "contractNo", "c.code",
            "plannedStartDate", "l.planned_start_date",
            "plannedEndDate", "l.planned_end_date",
            "status", "l.status",
            "updatedAt", "l.updated_at"
    );

    private static final String SELECT = """
            SELECT l.id,
                   l.lease_no,
                   l.tenant_id,
                   t.name AS tenant_name,
                   l.contract_id,
                   c.code AS contract_no,
                   l.status,
                   l.planned_start_date,
                   l.planned_end_date,
                   l.actual_checkin_at,
                   l.actual_checkout_at,
                   l.checkout_reason,
                   l.renewal_of_lease_id,
                   l.version,
                   l.created_at,
                   l.updated_at,
                   (SELECT COUNT(*) FROM lease_room lr
                     WHERE lr.lease_id = l.id AND lr.deleted = 0) AS room_count,
                   (SELECT COUNT(*) FROM lease_room lr
                     WHERE lr.lease_id = l.id AND lr.deleted = 0 AND lr.status = 'ACTIVE') AS active_room_count,
                   (SELECT COUNT(*) FROM lease_room lr
                     WHERE lr.lease_id = l.id AND lr.deleted = 0 AND lr.status = 'TERMINATED') AS terminated_room_count,
                   (SELECT GROUP_CONCAT(lr.room_id ORDER BY lr.id SEPARATOR ',') FROM lease_room lr
                     WHERE lr.lease_id = l.id AND lr.deleted = 0) AS room_ids,
                   (SELECT GROUP_CONCAT(lr.room_name_snapshot ORDER BY lr.id SEPARATOR ',') FROM lease_room lr
                     WHERE lr.lease_id = l.id AND lr.deleted = 0) AS room_names,
                   (SELECT GROUP_CONCAT(lr.status ORDER BY lr.id SEPARATOR ',') FROM lease_room lr
                     WHERE lr.lease_id = l.id AND lr.deleted = 0) AS room_statuses
            FROM lease l
            JOIN tenant t ON t.id = l.tenant_id AND t.deleted = 0
            JOIN contract c ON c.id = l.contract_id AND c.deleted = 0
            """;

    private final JdbcTemplate jdbcTemplate;
    private final CustomFieldQueryService customFieldQueryService;
    private final CustomFieldProjectionService customFieldProjectionService;

    public LeaseRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null, null);
    }

    @Autowired
    public LeaseRepository(
            JdbcTemplate jdbcTemplate,
            CustomFieldQueryService customFieldQueryService,
            CustomFieldProjectionService customFieldProjectionService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.customFieldQueryService = customFieldQueryService;
        this.customFieldProjectionService = customFieldProjectionService;
    }

    public PageResponse<LeaseResponse> search(
            String keyword,
            String status,
            Long tenantId,
            Long contractId,
            String sortField,
            String sortDirection,
            Integer page,
            Integer pageSize
    ) {
        return search(keyword, status, tenantId, contractId, sortField, sortDirection, null, null, null, page, pageSize);
    }

    public PageResponse<LeaseResponse> search(
            String keyword,
            String status,
            Long tenantId,
            Long contractId,
            String sortField,
            String sortDirection,
            String sorts,
            String groupBy,
            String customFilters,
            Integer page,
            Integer pageSize
    ) {
        List<Object> args = new ArrayList<>();
        String where = where(keyword, status, tenantId, contractId, args);
        CompiledQuery customQuery = compileCustomQuery(
                customFilters,
                groupBy,
                sorts,
                NativeOrderPolicy.of(SORT_FIELDS, sortField, sortDirection, "l.updated_at", "l.id")
        );
        if (customQuery != null && customQuery.hasPredicates()) {
            where += " AND " + customQuery.predicateSql();
            args.addAll(customQuery.predicateArguments());
        }
        String orderBy = customQuery == null || !customQuery.hasOrderings()
                ? WorkspaceOrderBy.build(SORT_FIELDS, groupBy, sorts, sortField, sortDirection, "l.updated_at", "l.id")
                : String.join(", ", customQuery.orderExpressions());
        PageResponse<LeaseResponse> result = JdbcPagination.query(
                jdbcTemplate,
                SELECT + where + " ORDER BY " + orderBy,
                """
                SELECT COUNT(*) FROM lease l
                JOIN tenant t ON t.id = l.tenant_id AND t.deleted = 0
                JOIN contract c ON c.id = l.contract_id AND c.deleted = 0
                """ + where,
                args,
                customQuery == null ? List.of() : customQuery.orderArguments(),
                page,
                pageSize,
                leaseRowMapper()
        );
        if (customFieldProjectionService != null) {
            Map<Long, Map<String, Object>> values = customFieldProjectionService.values(
                    "leases",
                    result.records().stream().map(LeaseResponse::id).toList()
            );
            result = new PageResponse<>(
                    result.records().stream()
                            .map(lease -> lease.withCustomFields(values.getOrDefault(lease.id(), Map.of())))
                            .toList(),
                    result.total(),
                    result.page(),
                    result.pageSize()
            );
        }
        return result;
    }

    public PageResponse<LeaseResponse> search(
            String keyword,
            String status,
            Long tenantId,
            Long contractId,
            String sortField,
            String sortDirection,
            String sorts,
            String groupBy,
            Integer page,
            Integer pageSize
    ) {
        return search(
                keyword, status, tenantId, contractId, sortField, sortDirection,
                sorts, groupBy, null, page, pageSize
        );
    }

    public LeaseSummaryResponse summary(String keyword, String status, Long tenantId, Long contractId) {
        return summary(keyword, status, tenantId, contractId, null);
    }

    public LeaseSummaryResponse summary(
            String keyword,
            String status,
            Long tenantId,
            Long contractId,
            String customFilters
    ) {
        List<Object> args = new ArrayList<>();
        String where = where(keyword, status, tenantId, contractId, args);
        CompiledQuery customQuery = compileCustomQuery(customFilters, null, null, null);
        if (customQuery != null && customQuery.hasPredicates()) {
            where += " AND " + customQuery.predicateSql();
            args.addAll(customQuery.predicateArguments());
        }
        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT COUNT(*) AS total,
                       SUM(CASE WHEN l.status = 'PENDING_CHECKIN' THEN 1 ELSE 0 END) AS pending_count,
                       SUM(CASE WHEN l.status = 'PARTIALLY_ACTIVE' THEN 1 ELSE 0 END) AS partial_active_count,
                       SUM(CASE WHEN l.status = 'ACTIVE' THEN 1 ELSE 0 END) AS active_count,
                       SUM(CASE WHEN l.status = 'PARTIALLY_TERMINATED' THEN 1 ELSE 0 END) AS partial_terminated_count,
                       SUM(CASE WHEN l.status = 'TERMINATED' THEN 1 ELSE 0 END) AS terminated_count
                FROM lease l
                JOIN tenant t ON t.id = l.tenant_id AND t.deleted = 0
                JOIN contract c ON c.id = l.contract_id AND c.deleted = 0
                """ + where,
                args.toArray()
        );
        return new LeaseSummaryResponse(
                number(row, "total"),
                number(row, "pending_count"),
                number(row, "partial_active_count"),
                number(row, "active_count"),
                number(row, "partial_terminated_count"),
                number(row, "terminated_count")
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
                "leases",
                customFilters,
                groupBy,
                sorts,
                orderPolicy
        );
    }

    public LeaseResponse find(Long id) {
        List<LeaseResponse> rows = jdbcTemplate.query(
                SELECT + " WHERE l.id = ? AND l.deleted = 0",
                leaseRowMapper(),
                id
        );
        if (rows.isEmpty()) throw new BusinessException(404, "租约不存在");
        return rows.getFirst();
    }

    public Long uniqueActionableLeaseIdForContract(Long contractId) {
        List<Long> ids = jdbcTemplate.queryForList(
                """
                SELECT id FROM lease
                WHERE contract_id = ? AND deleted = 0
                  AND status IN ('PENDING_CHECKIN', 'PARTIALLY_ACTIVE', 'ACTIVE', 'PARTIALLY_TERMINATED')
                ORDER BY id DESC
                LIMIT 2
                """,
                Long.class,
                contractId
        );
        if (ids.isEmpty()) return null;
        if (ids.size() > 1) throw new BusinessException(409, "合同存在多个可操作租约，请指定租约办理入住");
        return ids.getFirst();
    }

    public Long uniqueLeaseIdForRoom(Long roomId, Long contractId, LeaseRoomStatus roomStatus) {
        List<Object> args = new ArrayList<>();
        args.add(roomId);
        args.add(roomStatus.name());
        String contractFilter = "";
        if (contractId != null) {
            contractFilter = " AND l.contract_id = ?";
            args.add(contractId);
        }
        List<Long> ids = jdbcTemplate.queryForList(
                """
                SELECT l.id
                FROM lease_room lr
                JOIN lease l ON l.id = lr.lease_id AND l.deleted = 0
                WHERE lr.room_id = ? AND lr.status = ? AND lr.deleted = 0
                  AND l.status IN ('PENDING_CHECKIN', 'PARTIALLY_ACTIVE', 'ACTIVE',
                                   'RENEWING', 'CHECKOUT_PENDING', 'PARTIALLY_TERMINATED')
                """ + contractFilter + " ORDER BY l.id DESC LIMIT 2",
                Long.class,
                args.toArray()
        );
        if (ids.size() > 1) {
            throw new BusinessException(409, "该房间存在多个可操作租约，请改用租约工作台指定租约");
        }
        return ids.isEmpty() ? null : ids.getFirst();
    }

    public LeaseSnapshot lockLease(Long id) {
        List<LeaseSnapshot> rows = jdbcTemplate.query(
                """
                SELECT id, lease_no, tenant_id, contract_id, contract_revision_id, status,
                       planned_start_date, planned_end_date, renewal_of_lease_id, version
                FROM lease
                WHERE id = ? AND deleted = 0
                FOR UPDATE
                """,
                (rs, rowNum) -> new LeaseSnapshot(
                        rs.getLong("id"),
                        rs.getString("lease_no"),
                        rs.getLong("tenant_id"),
                        rs.getLong("contract_id"),
                        nullableLong(rs, "contract_revision_id"),
                        LeaseStatus.valueOf(rs.getString("status")),
                        rs.getObject("planned_start_date", LocalDate.class),
                        rs.getObject("planned_end_date", LocalDate.class),
                        nullableLong(rs, "renewal_of_lease_id"),
                        rs.getLong("version")
                ),
                id
        );
        if (rows.isEmpty()) throw new BusinessException(404, "租约不存在");
        return rows.getFirst();
    }

    public ContractLeaseSnapshot lockContract(Long contractId) {
        List<ContractLeaseSnapshot> rows = jdbcTemplate.query(
                """
                SELECT c.id, c.code, c.tenant_id, c.status, c.start_date, c.end_date,
                       c.current_revision_no, c.version,
                       (SELECT cr.id FROM contract_revision cr
                         WHERE cr.contract_id = c.id AND cr.deleted = 0
                         ORDER BY cr.revision_no DESC LIMIT 1) AS revision_id
                FROM contract c
                WHERE c.id = ? AND c.deleted = 0
                FOR UPDATE
                """,
                (rs, rowNum) -> new ContractLeaseSnapshot(
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getLong("tenant_id"),
                        ContractStatus.fromDatabase(rs.getString("status")),
                        rs.getObject("start_date", LocalDate.class),
                        rs.getObject("end_date", LocalDate.class),
                        rs.getInt("current_revision_no"),
                        rs.getLong("version"),
                        nullableLong(rs, "revision_id")
                ),
                contractId
        );
        if (rows.isEmpty()) throw new BusinessException(404, "合同不存在");
        return rows.getFirst();
    }

    public void ensureTenantAvailable(Long tenantId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tenant
                WHERE id = ? AND deleted = 0 AND status IN ('ACTIVE', 'ENABLED')
                """,
                Integer.class,
                tenantId
        );
        if (count == null || count == 0) {
            throw new BusinessException(400, "合同关联租户不存在或已停用");
        }
    }

    public List<Long> contractRoomIds(Long contractId) {
        return jdbcTemplate.queryForList(
                "SELECT room_id FROM contract_room WHERE contract_id = ? AND deleted = 0 ORDER BY id",
                Long.class,
                contractId
        );
    }

    public List<Long> leaseRoomIds(Long leaseId) {
        return jdbcTemplate.queryForList(
                "SELECT room_id FROM lease_room WHERE lease_id = ? AND deleted = 0 ORDER BY id",
                Long.class,
                leaseId
        );
    }

    public Long create(
            LeaseCreateRequest request,
            ContractLeaseSnapshot contract,
            List<Long> roomIds,
            Long renewalOfLeaseId,
            Long operatorId
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        String leaseNo = request.leaseNo() == null || request.leaseNo().isBlank()
                ? generatedLeaseNo()
                : request.leaseNo().trim();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        """
                        INSERT INTO lease (
                            lease_no, tenant_id, contract_id, contract_revision_id, status,
                            planned_start_date, planned_end_date, renewal_of_lease_id,
                            version, created_by, updated_by, remark
                        ) VALUES (?, ?, ?, ?, 'PENDING_CHECKIN', ?, ?, ?, 0, ?, ?, ?)
                        """,
                        Statement.RETURN_GENERATED_KEYS
                );
                statement.setString(1, leaseNo);
                statement.setLong(2, contract.tenantId());
                statement.setLong(3, contract.id());
                if (contract.revisionId() == null) statement.setObject(4, null); else statement.setLong(4, contract.revisionId());
                statement.setDate(5, Date.valueOf(request.plannedStartDate()));
                statement.setDate(6, Date.valueOf(request.plannedEndDate()));
                if (renewalOfLeaseId == null) statement.setObject(7, null); else statement.setLong(7, renewalOfLeaseId);
                statement.setLong(8, operatorId);
                statement.setLong(9, operatorId);
                statement.setString(10, request.remark());
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "租约编号已存在且不可复用");
        }
        Number key = keyHolder.getKey();
        if (key == null) throw new BusinessException(500, "租约创建失败");
        Long leaseId = key.longValue();
        insertLeaseRooms(leaseId, roomIds, operatorId);
        return leaseId;
    }

    public void lockPhysicalRooms(List<Long> roomIds) {
        if (roomIds.isEmpty()) return;
        String placeholders = placeholders(roomIds.size());
        List<Long> locked = jdbcTemplate.queryForList(
                "SELECT id FROM building_room WHERE id IN (" + placeholders + ") AND deleted = 0 ORDER BY id FOR UPDATE",
                Long.class,
                roomIds.toArray()
        );
        if (new LinkedHashSet<>(locked).size() != new LinkedHashSet<>(roomIds).size()) {
            throw new BusinessException(400, "存在无效房间");
        }
    }

    public void ensureRoomsAvailableForCheckIn(List<Long> roomIds) {
        if (roomIds.isEmpty()) return;
        String placeholders = placeholders(roomIds.size());
        Integer unavailable = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM building_room
                WHERE id IN (
                """ + placeholders + """
                ) AND deleted = 0
                  AND UPPER(COALESCE(rent_status, 'VACANT')) NOT IN ('VACANT', 'AVAILABLE')
                """,
                Integer.class,
                roomIds.toArray()
        );
        if (unavailable != null && unavailable > 0) {
            throw new BusinessException(409, "目标房间当前不是空置状态，请先核对历史占用数据");
        }
    }

    public List<LeaseRoomSnapshot> lockLeaseRooms(Long leaseId) {
        return jdbcTemplate.query(
                """
                SELECT id, lease_id, room_id, status, occupancy_start_at, occupancy_end_at, version
                FROM lease_room
                WHERE lease_id = ? AND deleted = 0
                ORDER BY room_id
                FOR UPDATE
                """,
                (rs, rowNum) -> new LeaseRoomSnapshot(
                        rs.getLong("id"),
                        rs.getLong("lease_id"),
                        rs.getLong("room_id"),
                        LeaseRoomStatus.valueOf(rs.getString("status")),
                        dateTime(rs, "occupancy_start_at"),
                        dateTime(rs, "occupancy_end_at"),
                        rs.getLong("version")
                ),
                leaseId
        );
    }

    public void ensureNoOverlappingOccupancy(
            Long leaseId,
            List<Long> roomIds,
            LocalDateTime start,
            LocalDateTime end
    ) {
        if (roomIds.isEmpty()) return;
        String placeholders = placeholders(roomIds.size());
        List<Object> args = new ArrayList<>(roomIds);
        args.add(leaseId);
        args.add(end);
        args.add(start);
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM lease_room lr
                JOIN lease l ON l.id = lr.lease_id AND l.deleted = 0
                WHERE lr.deleted = 0
                  AND lr.status = 'ACTIVE'
                  AND lr.room_id IN (
                """ + placeholders + """
                  )
                  AND lr.lease_id <> ?
                  AND lr.occupancy_start_at <= ?
                  AND (lr.occupancy_end_at IS NULL OR lr.occupancy_end_at >= ?)
                """,
                Integer.class,
                args.toArray()
        );
        if (count != null && count > 0) {
            throw new BusinessException(409, "目标房间存在重叠的有效租约占用");
        }
    }

    public void checkInRoom(LeaseRoomSnapshot room, LocalDateTime occurredAt, Long operatorId) {
        try {
            int updated = jdbcTemplate.update(
                    """
                    UPDATE lease_room
                    SET status = 'ACTIVE', occupancy_start_at = ?, occupancy_end_at = NULL,
                        version = version + 1, updated_by = ?
                    WHERE id = ? AND status = 'PENDING_CHECKIN' AND version = ? AND deleted = 0
                    """,
                    occurredAt, operatorId, room.id(), room.version()
            );
            requireRoomUpdate(updated);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "房间已被其他有效租约占用");
        }
        jdbcTemplate.update(
                "UPDATE building_room SET rent_status = 'RENTED', updated_by = ? WHERE id = ? AND deleted = 0",
                operatorId,
                room.roomId()
        );
    }

    public void checkOutRoom(LeaseRoomSnapshot room, LocalDateTime occurredAt, Long operatorId) {
        int updated = jdbcTemplate.update(
                """
                UPDATE lease_room
                SET status = 'TERMINATED', occupancy_end_at = ?, version = version + 1, updated_by = ?
                WHERE id = ? AND status = 'ACTIVE' AND version = ? AND deleted = 0
                """,
                occurredAt, operatorId, room.id(), room.version()
        );
        requireRoomUpdate(updated);
        jdbcTemplate.update(
                """
                UPDATE building_room r
                SET r.rent_status = 'VACANT', r.updated_by = ?
                WHERE r.id = ? AND r.deleted = 0
                  AND NOT EXISTS (
                    SELECT 1 FROM lease_room active_lr
                    WHERE active_lr.room_id = r.id AND active_lr.deleted = 0
                      AND active_lr.status = 'ACTIVE' AND active_lr.occupancy_end_at IS NULL
                  )
                """,
                operatorId,
                room.roomId()
        );
    }

    public void cancelPendingRoom(LeaseRoomSnapshot room, Long operatorId) {
        int updated = jdbcTemplate.update(
                """
                UPDATE lease_room
                SET status = 'CANCELLED', version = version + 1, updated_by = ?
                WHERE id = ? AND status = 'PENDING_CHECKIN' AND version = ? AND deleted = 0
                """,
                operatorId, room.id(), room.version()
        );
        requireRoomUpdate(updated);
    }

    public LeaseCounts counts(Long leaseId) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT COUNT(*) AS total,
                       SUM(CASE WHEN status = 'ACTIVE' THEN 1 ELSE 0 END) AS active_count,
                       SUM(CASE WHEN status = 'TERMINATED' THEN 1 ELSE 0 END) AS terminated_count,
                       SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_count
                FROM lease_room
                WHERE lease_id = ? AND deleted = 0
                """,
                leaseId
        );
        return new LeaseCounts(
                (int) number(row, "total"),
                (int) number(row, "active_count"),
                (int) number(row, "terminated_count"),
                (int) number(row, "cancelled_count")
        );
    }

    public long updateLeaseStatus(
            LeaseSnapshot lease,
            LeaseStatus status,
            LocalDateTime checkinAt,
            LocalDateTime checkoutAt,
            String checkoutReason,
            Long operatorId
    ) {
        int updated = jdbcTemplate.update(
                """
                UPDATE lease
                SET status = ?,
                    actual_checkin_at = COALESCE(actual_checkin_at, ?),
                    actual_checkout_at = ?,
                    checkout_reason = COALESCE(?, checkout_reason),
                    version = version + 1,
                    updated_by = ?
                WHERE id = ? AND version = ? AND deleted = 0
                """,
                status.name(), checkinAt, checkoutAt, checkoutReason, operatorId, lease.id(), lease.version()
        );
        if (updated == 0) throw new BusinessException(409, "租约已被其他操作更新，请刷新后重试");
        return lease.version() + 1;
    }

    public void addStatusHistory(
            Long leaseId,
            LeaseStatus from,
            LeaseStatus to,
            String reason,
            Long operatorId,
            long aggregateVersion,
            String requestId
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO lease_status_history (
                    lease_id, from_status, to_status, reason, operator_id, aggregate_version, request_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                leaseId, from == null ? null : from.name(), to.name(), reason,
                operatorId, aggregateVersion, requestId
        );
    }

    public String addEvent(Long leaseId, String eventType, String payload, Long operatorId) {
        String eventId = UUID.randomUUID().toString();
        jdbcTemplate.update(
                """
                INSERT INTO lease_event (event_id, lease_id, event_type, event_payload, operator_id)
                VALUES (?, ?, ?, CAST(? AS JSON), ?)
                """,
                eventId, leaseId, eventType, payload == null ? "{}" : payload, operatorId
        );
        return eventId;
    }

    public long countOtherLiveLeases(Long contractId, Long excludedLeaseId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM lease
                WHERE contract_id = ? AND id <> ? AND deleted = 0
                  AND status IN ('PENDING_CHECKIN', 'PARTIALLY_ACTIVE', 'ACTIVE', 'RENEWING', 'CHECKOUT_PENDING', 'PARTIALLY_TERMINATED')
                """,
                Long.class,
                contractId,
                excludedLeaseId
        );
        return count == null ? 0 : count;
    }

    public void markRenewing(LeaseSnapshot lease, Long operatorId) {
        int updated = jdbcTemplate.update(
                "UPDATE lease SET status = 'RENEWING', version = version + 1, updated_by = ? WHERE id = ? AND version = ? AND deleted = 0",
                operatorId, lease.id(), lease.version()
        );
        if (updated == 0) throw new BusinessException(409, "租约已被其他操作更新，请刷新后重试");
    }

    private void insertLeaseRooms(Long leaseId, List<Long> roomIds, Long operatorId) {
        lockPhysicalRooms(roomIds);
        String placeholders = placeholders(roomIds.size());
        List<RoomSnapshot> snapshots = jdbcTemplate.query(
                """
                SELECT r.id, r.code, r.room_no, b.name AS building_name, f.name AS floor_name
                FROM building_room r
                JOIN building b ON b.id = r.building_id AND b.deleted = 0
                JOIN building_floor f ON f.id = r.floor_id AND f.deleted = 0
                WHERE r.id IN (
                """ + placeholders + ") AND r.deleted = 0 ORDER BY r.id",
                (rs, rowNum) -> new RoomSnapshot(
                        rs.getLong("id"), rs.getString("code"), rs.getString("room_no"),
                        rs.getString("building_name"), rs.getString("floor_name")
                ),
                roomIds.toArray()
        );
        if (snapshots.size() != new LinkedHashSet<>(roomIds).size()) {
            throw new BusinessException(400, "存在无效房间");
        }
        for (RoomSnapshot room : snapshots) {
            jdbcTemplate.update(
                    """
                    INSERT INTO lease_room (
                        lease_id, room_id, status, room_code_snapshot, room_name_snapshot,
                        building_name_snapshot, floor_name_snapshot, version, created_by, updated_by
                    ) VALUES (?, ?, 'PENDING_CHECKIN', ?, ?, ?, ?, 0, ?, ?)
                    """,
                    leaseId, room.id(), room.code(), room.roomName(), room.buildingName(), room.floorName(),
                    operatorId, operatorId
            );
        }
    }

    private String where(String keyword, String status, Long tenantId, Long contractId, List<Object> args) {
        StringBuilder where = new StringBuilder(" WHERE l.deleted = 0");
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (l.lease_no LIKE ? OR c.code LIKE ? OR t.name LIKE ?)");
            String pattern = "%" + keyword.trim() + "%";
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
        if (status != null && !status.isBlank()) {
            try {
                LeaseStatus.valueOf(status.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(400, "租约状态筛选值无效");
            }
            where.append(" AND l.status = ?");
            args.add(status.toUpperCase(Locale.ROOT));
        }
        if (tenantId != null) {
            where.append(" AND l.tenant_id = ?");
            args.add(tenantId);
        }
        if (contractId != null) {
            where.append(" AND l.contract_id = ?");
            args.add(contractId);
        }
        return where.toString();
    }

    private RowMapper<LeaseResponse> leaseRowMapper() {
        return (rs, rowNum) -> new LeaseResponse(
                rs.getLong("id"),
                rs.getString("lease_no"),
                rs.getLong("tenant_id"),
                rs.getString("tenant_name"),
                rs.getLong("contract_id"),
                rs.getString("contract_no"),
                LeaseStatus.valueOf(rs.getString("status")),
                date(rs, "planned_start_date"),
                date(rs, "planned_end_date"),
                dateTime(rs, "actual_checkin_at"),
                dateTime(rs, "actual_checkout_at"),
                rs.getString("checkout_reason"),
                nullableLong(rs, "renewal_of_lease_id"),
                rs.getLong("version"),
                rs.getInt("room_count"),
                rs.getInt("active_room_count"),
                rs.getInt("terminated_room_count"),
                longList(rs.getString("room_ids")),
                stringList(rs.getString("room_names")),
                leaseRoomStatusList(rs.getString("room_statuses")),
                dateTime(rs, "created_at"),
                dateTime(rs, "updated_at")
        );
    }

    private String generatedLeaseNo() {
        return "LEA-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT);
    }

    private String placeholders(int count) {
        return java.util.Collections.nCopies(count, "?").stream().collect(Collectors.joining(","));
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

    private List<LeaseRoomStatus> leaseRoomStatusList(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(LeaseRoomStatus::valueOf).toList();
    }

    private long number(Map<String, Object> row, String name) {
        Object value = row.get(name);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private void requireRoomUpdate(int updated) {
        if (updated == 0) throw new BusinessException(409, "租约房间状态已变化，请刷新后重试");
    }

    private record RoomSnapshot(Long id, String code, String roomName, String buildingName, String floorName) {
    }

    public record ContractLeaseSnapshot(
            Long id,
            String contractNo,
            Long tenantId,
            ContractStatus status,
            LocalDate startDate,
            LocalDate endDate,
            int currentRevisionNo,
            long version,
            Long revisionId
    ) {
    }

    public record LeaseSnapshot(
            Long id,
            String leaseNo,
            Long tenantId,
            Long contractId,
            Long contractRevisionId,
            LeaseStatus status,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            Long renewalOfLeaseId,
            long version
    ) {
    }

    public record LeaseRoomSnapshot(
            Long id,
            Long leaseId,
            Long roomId,
            LeaseRoomStatus status,
            LocalDateTime occupancyStartAt,
            LocalDateTime occupancyEndAt,
            long version
    ) {
    }

    public record LeaseCounts(int total, int active, int terminated, int cancelled) {
    }
}
