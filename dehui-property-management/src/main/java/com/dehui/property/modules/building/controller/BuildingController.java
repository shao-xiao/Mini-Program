package com.dehui.property.modules.building.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcPagination;
import com.dehui.property.common.JdbcMaps;
import com.dehui.property.common.PageResponse;
import com.dehui.property.common.WorkspaceOrderBy;
import com.dehui.property.modules.customfield.application.CustomFieldProjectionService;
import com.dehui.property.modules.customfield.application.CustomFieldQueryCompiler.CompiledQuery;
import com.dehui.property.modules.customfield.application.CustomFieldQueryCompiler.NativeOrderPolicy;
import com.dehui.property.modules.customfield.application.CustomFieldQueryService;
import com.dehui.property.modules.lease.application.LeaseApplicationService;
import com.dehui.property.modules.lease.dto.LeaseResponse;
import com.dehui.property.security.AuthInterceptor;
import com.dehui.property.security.AuthPrincipal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BuildingController {

    public static final String BUILDING_LIST_SQL = """
            SELECT id,
                   code AS buildingCode,
                   name AS buildingName,
                   address,
                   total_floor AS totalFloors,
                   status,
                   remark AS description,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM building
            WHERE deleted = 0
            ORDER BY updated_at DESC, id DESC
            """;

    public static final String INSERT_BUILDING_SQL = """
            INSERT INTO building (code, name, address, total_floor, status, created_by, updated_by, remark)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE_BUILDING_SQL = """
            UPDATE building
            SET code = ?,
                name = ?,
                address = ?,
                total_floor = ?,
                status = ?,
                updated_by = ?,
                remark = ?
            WHERE id = ? AND deleted = 0
            """;

    private static final Map<String, String> ROOM_ORDERABLE_FIELDS = Map.ofEntries(
            Map.entry("roomCode", "r.code"),
            Map.entry("buildingName", "b.name"),
            Map.entry("floorName", "f.name"),
            Map.entry("roomNumber", "r.room_no"),
            Map.entry("roomName", "r.room_no"),
            Map.entry("area", "r.area"),
            Map.entry("propertyFeeArea", "r.property_fee_area"),
            Map.entry("roomType", "r.usage_type"),
            Map.entry("status", "r.rent_status")
    );

    private final JdbcTemplate jdbcTemplate;
    private final LeaseApplicationService leaseService;
    private final CustomFieldQueryService customFieldQueryService;
    private final CustomFieldProjectionService customFieldProjectionService;

    public BuildingController(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null, null, null);
    }

    public BuildingController(JdbcTemplate jdbcTemplate, LeaseApplicationService leaseService) {
        this(jdbcTemplate, leaseService, null, null);
    }

    @Autowired
    public BuildingController(
            JdbcTemplate jdbcTemplate,
            LeaseApplicationService leaseService,
            CustomFieldQueryService customFieldQueryService,
            CustomFieldProjectionService customFieldProjectionService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.leaseService = leaseService;
        this.customFieldQueryService = customFieldQueryService;
        this.customFieldProjectionService = customFieldProjectionService;
    }

    @GetMapping("/buildings")
    public ApiResponse<List<Map<String, Object>>> buildings() {
        return ApiResponse.success(jdbcTemplate.queryForList(BUILDING_LIST_SQL));
    }

    @PostMapping("/buildings")
    public ApiResponse<Void> createBuilding(@RequestBody BuildingRequest request) {
        BuildingInput input = normalize(request, null);
        try {
            jdbcTemplate.update(INSERT_BUILDING_SQL, input.code(), input.name(), input.address(), input.totalFloors(),
                    input.status(), 0L, 0L, input.description());
            return ApiResponse.success();
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(400, "Building code or name already exists");
        }
    }

    @PutMapping("/buildings/{id}")
    public ApiResponse<Void> updateBuilding(@PathVariable Long id, @RequestBody BuildingRequest request) {
        BuildingInput input = normalize(request, id);
        try {
            int updated = jdbcTemplate.update(UPDATE_BUILDING_SQL, input.code(), input.name(), input.address(),
                    input.totalFloors(), input.status(), 0L, input.description(), id);
            ensureUpdated(updated, "Building not found");
            return ApiResponse.success();
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(400, "Building code or name already exists");
        }
    }

    @DeleteMapping("/buildings/{id}")
    public ApiResponse<Void> deleteBuilding(@PathVariable Long id) {
        Long children = jdbcTemplate.queryForObject(
                """
                SELECT
                  (SELECT COUNT(*) FROM building_floor WHERE building_id = ? AND deleted = 0) +
                  (SELECT COUNT(*) FROM building_room WHERE building_id = ? AND deleted = 0)
                """,
                Long.class,
                id,
                id
        );
        if (children != null && children > 0) {
            throw new BusinessException(400, "Building has floors or rooms");
        }

        int updated = jdbcTemplate.update(
                "UPDATE building SET deleted = 1, updated_by = ? WHERE id = ? AND deleted = 0",
                0L,
                id
        );
        ensureUpdated(updated, "Building not found");
        return ApiResponse.success();
    }

    @GetMapping("/floors")
    public ApiResponse<List<Map<String, Object>>> floors(@RequestParam(name = "building_id", required = false) Long buildingId) {
        String baseSql = """
                SELECT id,
                       code AS floorCode,
                       building_id AS buildingId,
                       floor_no AS floorNumber,
                       name AS floorName,
                       total_area AS totalArea,
                       status,
                       remark AS description
                FROM building_floor
                WHERE deleted = 0
                """;
        if (buildingId == null) {
            return ApiResponse.success(jdbcTemplate.queryForList(baseSql + " ORDER BY building_id ASC, floor_no ASC, id ASC"));
        }
        return ApiResponse.success(jdbcTemplate.queryForList(
                baseSql + " AND building_id = ? ORDER BY floor_no ASC, id ASC",
                buildingId
        ));
    }

    @GetMapping("/buildings/{buildingId}/floors")
    public ApiResponse<List<Map<String, Object>>> buildingFloors(@PathVariable Long buildingId) {
        return floors(buildingId);
    }

    @PostMapping("/floors")
    public ApiResponse<Void> createFloor(@RequestBody Map<String, Object> body) {
        Long buildingId = JdbcMaps.requiredLong(body, "Building is required", "buildingId", "building_id");
        Integer floorNo = JdbcMaps.intVal(body, null, "floorNumber", "floorNo", "floor_no");
        if (floorNo == null) {
            throw new BusinessException(400, "Floor number is required");
        }
        String name = JdbcMaps.strOr(body, floorNo + "F", "floorName", "name");
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO building_floor (code, building_id, floor_no, name, total_area, status, created_by, updated_by, remark)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    JdbcMaps.code("FLR"),
                    buildingId,
                    floorNo,
                    name,
                    JdbcMaps.decimal(body, java.math.BigDecimal.ZERO, "totalArea", "total_area"),
                    JdbcMaps.strOr(body, "ACTIVE", "status"),
                    0L,
                    0L,
                    JdbcMaps.str(body, "description", "remark")
            );
            return ApiResponse.success();
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(400, "Floor already exists");
        }
    }

    @PutMapping("/floors/{id}")
    public ApiResponse<Void> updateFloor(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long buildingId = JdbcMaps.requiredLong(body, "Building is required", "buildingId", "building_id");
        Integer floorNo = JdbcMaps.intVal(body, null, "floorNumber", "floorNo", "floor_no");
        if (floorNo == null) {
            throw new BusinessException(400, "Floor number is required");
        }
        int updated = jdbcTemplate.update(
                """
                UPDATE building_floor
                SET building_id = ?, floor_no = ?, name = ?, total_area = ?, status = ?, updated_by = ?, remark = ?
                WHERE id = ? AND deleted = 0
                """,
                buildingId,
                floorNo,
                JdbcMaps.strOr(body, floorNo + "F", "floorName", "name"),
                JdbcMaps.decimal(body, java.math.BigDecimal.ZERO, "totalArea", "total_area"),
                JdbcMaps.strOr(body, "ACTIVE", "status"),
                0L,
                JdbcMaps.str(body, "description", "remark"),
                id
        );
        ensureUpdated(updated, "Floor not found");
        return ApiResponse.success();
    }

    @DeleteMapping("/floors/{id}")
    public ApiResponse<Void> deleteFloor(@PathVariable Long id) {
        Long rooms = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM building_room WHERE floor_id = ? AND deleted = 0",
                Long.class,
                id
        );
        if (rooms != null && rooms > 0) {
            throw new BusinessException(400, "Floor has rooms");
        }
        int updated = jdbcTemplate.update("UPDATE building_floor SET deleted = 1, updated_by = ? WHERE id = ? AND deleted = 0", 0L, id);
        ensureUpdated(updated, "Floor not found");
        return ApiResponse.success();
    }

    @PostMapping("/floors/batch-generate")
    public ApiResponse<Void> batchGenerateFloors(@RequestBody Map<String, Object> body) {
        Long buildingId = JdbcMaps.requiredLong(body, "Building is required", "buildingId", "building_id");
        int basementStart = JdbcMaps.intVal(body, 0, "basementStart");
        int basementEnd = JdbcMaps.intVal(body, 0, "basementEnd");
        int aboveStart = JdbcMaps.intVal(body, 1, "aboveStart");
        int aboveEnd = JdbcMaps.intVal(body, 1, "aboveEnd");
        for (int no = -Math.abs(basementStart); no <= -Math.abs(basementEnd); no++) {
            insertFloorIfAbsent(buildingId, no, "B" + Math.abs(no));
        }
        for (int no = aboveStart; no <= aboveEnd; no++) {
            insertFloorIfAbsent(buildingId, no, no + "F");
        }
        return ApiResponse.success();
    }

    @GetMapping("/rooms")
    public ApiResponse<PageResponse<Map<String, Object>>> rooms(
            @RequestParam(name = "building_id", required = false) Long buildingId,
            @RequestParam(name = "floor_id", required = false) Long floorId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String customFilters,
            @RequestParam(required = false) String sorts,
            @RequestParam(required = false) String groupBy,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        StringBuilder sql = new StringBuilder("""
                SELECT r.id,
                       r.code AS roomCode,
                       r.building_id AS buildingId,
                       b.name AS buildingName,
                       r.floor_id AS floorId,
                       f.name AS floorName,
                       r.room_no AS roomNumber,
                       r.room_no AS roomName,
                       r.area,
                       r.property_fee_area AS propertyFeeArea,
                       r.usage_type AS roomType,
                       r.rent_status AS status,
                       COALESCE(contract_stage.contract_stage, 'NONE') AS contractStage,
                       r.remark AS description
                FROM building_room r
                LEFT JOIN building b ON b.id = r.building_id
                LEFT JOIN building_floor f ON f.id = r.floor_id
                LEFT JOIN (
                    SELECT contract_link.room_id,
                           CASE
                               WHEN MAX(CASE
                                   WHEN contract_link.status = 'ACTIVE'
                                    AND CURRENT_DATE BETWEEN COALESCE(contract_link.effective_date, contract_link.start_date)
                                                             AND contract_link.end_date
                                   THEN 1 ELSE 0
                               END) = 1 THEN 'ACTIVE_CURRENT'
                               WHEN MAX(CASE
                                   WHEN contract_link.status = 'ACTIVE'
                                    AND CURRENT_DATE < COALESCE(contract_link.effective_date, contract_link.start_date)
                                   THEN 1 ELSE 0
                               END) = 1 THEN 'ACTIVE_FUTURE'
                               WHEN MAX(CASE WHEN contract_link.status = 'SUSPENDED' THEN 1 ELSE 0 END) = 1
                                   THEN 'SUSPENDED'
                               WHEN MAX(CASE
                                   WHEN contract_link.status IN ('PENDING_APPROVAL', 'PENDING') THEN 1 ELSE 0
                               END) = 1 THEN 'PENDING_APPROVAL'
                               WHEN MAX(CASE WHEN contract_link.status = 'DRAFT' THEN 1 ELSE 0 END) = 1
                                   THEN 'DRAFT'
                               ELSE 'NONE'
                           END AS contract_stage
                    FROM (
                        SELECT cr.room_id,
                               c.status,
                               c.start_date,
                               c.end_date,
                               c.effective_date
                        FROM contract_room cr
                        JOIN contract c ON c.id = cr.contract_id AND c.deleted = 0
                        WHERE cr.deleted = 0

                        UNION ALL

                        SELECT c.room_id,
                               c.status,
                               c.start_date,
                               c.end_date,
                               c.effective_date
                        FROM contract c
                        LEFT JOIN contract_room cr
                          ON cr.contract_id = c.id
                         AND cr.deleted = 0
                        WHERE c.deleted = 0
                          AND c.room_id IS NOT NULL
                          AND cr.id IS NULL
                    ) contract_link
                    GROUP BY contract_link.room_id
                ) contract_stage ON contract_stage.room_id = r.id
                WHERE r.deleted = 0
                """);
        StringBuilder countSql = new StringBuilder("""
                SELECT COUNT(*)
                FROM building_room r
                WHERE r.deleted = 0
                """);
        List<Object> args = new java.util.ArrayList<>();
        if (buildingId != null) {
            sql.append(" AND r.building_id = ?");
            countSql.append(" AND r.building_id = ?");
            args.add(buildingId);
        }
        if (floorId != null) {
            sql.append(" AND r.floor_id = ?");
            countSql.append(" AND r.floor_id = ?");
            args.add(floorId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND r.rent_status = ?");
            countSql.append(" AND r.rent_status = ?");
            args.add(status);
        }
        CompiledQuery customQuery = compileRoomCustomQuery(customFilters, groupBy, sorts);
        if (customQuery != null && customQuery.hasPredicates()) {
            sql.append(" AND ").append(customQuery.predicateSql());
            countSql.append(" AND ").append(customQuery.predicateSql());
            args.addAll(customQuery.predicateArguments());
        }
        if (customQuery != null && customQuery.hasOrderings()) {
            sql.append(" ORDER BY ").append(String.join(", ", customQuery.orderExpressions()));
        } else if ((sorts == null || sorts.isBlank()) && (groupBy == null || groupBy.isBlank())) {
            sql.append(" ORDER BY r.room_no ASC, r.id ASC");
        } else {
            sql.append(" ORDER BY ").append(WorkspaceOrderBy.build(
                    ROOM_ORDERABLE_FIELDS,
                    groupBy,
                    sorts,
                    null,
                    null,
                    "r.room_no",
                    "r.id"
            ));
        }
        PageResponse<Map<String, Object>> result = JdbcPagination.query(
                jdbcTemplate,
                sql.toString(),
                countSql.toString(),
                args,
                customQuery == null ? List.of() : customQuery.orderArguments(),
                page,
                pageSize
        );
        if (customFieldProjectionService != null) {
            result = customFieldProjectionService.attach("rooms", result);
        }
        return ApiResponse.success(result);
    }

    public ApiResponse<PageResponse<Map<String, Object>>> rooms(
            Long buildingId,
            Long floorId,
            String status,
            String sorts,
            String groupBy,
            Integer page,
            Integer pageSize) {
        return rooms(buildingId, floorId, status, null, sorts, groupBy, page, pageSize);
    }

    public ApiResponse<PageResponse<Map<String, Object>>> rooms(
            Long buildingId,
            Long floorId,
            String status,
            Integer page,
            Integer pageSize) {
        return rooms(buildingId, floorId, status, null, null, null, page, pageSize);
    }

    private CompiledQuery compileRoomCustomQuery(String customFilters, String groupBy, String sorts) {
        if (customFieldQueryService == null) return null;
        boolean hasOrdering = (sorts != null && !sorts.isBlank()) || (groupBy != null && !groupBy.isBlank());
        NativeOrderPolicy orderPolicy = new NativeOrderPolicy(
                ROOM_ORDERABLE_FIELDS,
                null,
                null,
                "r.room_no",
                "ASC",
                "r.id",
                hasOrdering ? "DESC" : "ASC"
        );
        return customFieldQueryService.compile(
                "rooms",
                customFilters,
                groupBy,
                sorts,
                orderPolicy
        );
    }

    @GetMapping("/buildings/{buildingId}/floors/{floorId}/rooms")
    public ApiResponse<List<Map<String, Object>>> buildingFloorRooms(
            @PathVariable Long buildingId,
            @PathVariable Long floorId) {
        return ApiResponse.success(queryRoomList(buildingId, floorId));
    }

    @PostMapping("/rooms")
    public ApiResponse<Void> createRoom(@RequestBody Map<String, Object> body) {
        upsertRoom(null, body);
        return ApiResponse.success();
    }

    @PutMapping("/rooms/{id}")
    public ApiResponse<Void> updateRoom(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        upsertRoom(id, body);
        return ApiResponse.success();
    }

    @DeleteMapping("/rooms/{id}")
    public ApiResponse<Void> deleteRoom(@PathVariable Long id) {
        int updated = jdbcTemplate.update("UPDATE building_room SET deleted = 1, updated_by = ? WHERE id = ? AND deleted = 0", 0L, id);
        ensureUpdated(updated, "Room not found");
        return ApiResponse.success();
    }

    @PostMapping("/rooms/{id}/lease")
    public ApiResponse<LeaseResponse> leaseRoomV2(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(leaseService.legacyCheckInByRoom(
                id,
                JdbcMaps.longVal(body, "contractId", "contract_id"),
                JdbcMaps.str(body, "leaseNo", "lease_no"),
                optionalDate(body, "plannedStartDate", "startDate"),
                optionalDate(body, "plannedEndDate", "endDate"),
                JdbcMaps.str(body, "reason", "remark"),
                principal.userId()
        ));
    }

    public ApiResponse<Void> leaseRoom(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        int updated = jdbcTemplate.update(
                "UPDATE building_room SET rent_status = 'RENTED', updated_by = ?, remark = COALESCE(?, remark) WHERE id = ? AND deleted = 0",
                0L,
                JdbcMaps.str(body, "remark"),
                id
        );
        ensureUpdated(updated, "Room not found");
        return ApiResponse.success();
    }

    @PostMapping("/rooms/{id}/checkout")
    public ApiResponse<LeaseResponse> checkoutRoomV2(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(leaseService.legacyCheckOutByRoom(
                id, JdbcMaps.str(body, "reason", "remark"), principal.userId()
        ));
    }

    public ApiResponse<Void> checkoutRoom(@PathVariable Long id) {
        int updated = jdbcTemplate.update("UPDATE building_room SET rent_status = 'VACANT', updated_by = ? WHERE id = ? AND deleted = 0", 0L, id);
        ensureUpdated(updated, "Room not found");
        return ApiResponse.success();
    }

    private LocalDate optionalDate(Map<String, Object> body, String... keys) {
        String value = JdbcMaps.str(body, keys);
        return value == null ? null : LocalDate.parse(value.substring(0, Math.min(value.length(), 10)));
    }

    @GetMapping("/mobile/building/summary")
    public ApiResponse<Void> mobileSummary() {
        throw BusinessException.notImplemented("mobile building summary");
    }

    private void upsertRoom(Long id, Map<String, Object> body) {
        Long buildingId = JdbcMaps.requiredLong(body, "Building is required", "buildingId", "building_id");
        Long floorId = JdbcMaps.requiredLong(body, "Floor is required", "floorId", "floor_id");
        String roomNo = JdbcMaps.requiredStr(body, "Room number is required", "roomNumber", "roomNo", "room_no");
        requireFloorInBuilding(buildingId, floorId);
        java.math.BigDecimal area = JdbcMaps.decimal(body, java.math.BigDecimal.ZERO, "area");
        java.math.BigDecimal propertyFeeArea = JdbcMaps.decimal(body, area, "propertyFeeArea", "property_fee_area");
        if (area.signum() < 0 || propertyFeeArea.signum() < 0) {
            throw new BusinessException(400, "Room area cannot be negative");
        }
        if (id == null) {
            jdbcTemplate.update(
                    """
                    INSERT INTO building_room (
                        code, building_id, floor_id, room_no, area, property_fee_area,
                        usage_type, rent_status, status, created_by, updated_by, remark
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'VACANT', ?, ?, ?, ?)
                    """,
                    JdbcMaps.code("ROM"),
                    buildingId,
                    floorId,
                    roomNo,
                    area,
                    propertyFeeArea,
                    JdbcMaps.strOr(body, "OFFICE", "roomType", "usageType"),
                    "ACTIVE",
                    0L,
                    0L,
                    JdbcMaps.str(body, "description", "remark", "roomName")
            );
            return;
        }
        int updated = jdbcTemplate.update(
                """
                UPDATE building_room
                SET building_id = ?,
                    floor_id = ?,
                    room_no = ?,
                    area = ?,
                    property_fee_area = ?,
                    usage_type = ?,
                    updated_by = ?,
                    remark = ?
                WHERE id = ? AND deleted = 0
                """,
                buildingId,
                floorId,
                roomNo,
                area,
                propertyFeeArea,
                JdbcMaps.strOr(body, "OFFICE", "roomType", "usageType"),
                0L,
                JdbcMaps.str(body, "description", "remark", "roomName"),
                id
        );
        ensureUpdated(updated, "Room not found");
    }

    private List<Map<String, Object>> queryRoomList(Long buildingId, Long floorId) {
        return jdbcTemplate.queryForList(
                """
                SELECT r.id,
                       r.code AS roomCode,
                       r.building_id AS buildingId,
                       b.name AS buildingName,
                       r.floor_id AS floorId,
                       f.name AS floorName,
                       r.room_no AS roomNumber,
                       r.room_no AS roomName,
                       r.area,
                       r.property_fee_area AS propertyFeeArea,
                       r.usage_type AS roomType,
                       r.rent_status AS status,
                       r.remark AS description
                FROM building_room r
                LEFT JOIN building b ON b.id = r.building_id
                LEFT JOIN building_floor f ON f.id = r.floor_id
                WHERE r.deleted = 0
                  AND r.building_id = ?
                  AND r.floor_id = ?
                ORDER BY r.room_no ASC, r.id ASC
                """,
                buildingId,
                floorId
        );
    }

    private void requireFloorInBuilding(Long buildingId, Long floorId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM building_floor
                WHERE id = ?
                  AND building_id = ?
                  AND deleted = 0
                  AND status IN ('ACTIVE', 'ENABLED')
                """,
                Long.class,
                floorId,
                buildingId
        );
        if (count == null || count == 0) {
            throw new BusinessException(400, "Floor does not belong to the selected building or is disabled");
        }
    }

    private void insertFloorIfAbsent(Long buildingId, int floorNo, String name) {
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO building_floor (code, building_id, floor_no, name, status, created_by, updated_by)
                    VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
                    """,
                    JdbcMaps.code("FLR"),
                    buildingId,
                    floorNo,
                    name,
                    0L,
                    0L
            );
        } catch (DuplicateKeyException ignored) {
            // Existing floors are intentionally preserved during batch generation.
        }
    }

    private BuildingInput normalize(BuildingRequest request, Long id) {
        if (request == null || isBlank(request.buildingName())) {
            throw new BusinessException(400, "Building name is required");
        }

        String code = trimToNull(request.buildingCode());
        if (code == null && id != null) {
            code = jdbcTemplate.queryForObject(
                    "SELECT code FROM building WHERE id = ? AND deleted = 0",
                    String.class,
                    id
            );
        }
        if (code == null) {
            code = "BLD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        }

        int totalFloors = request.totalFloors() == null ? 0 : Math.max(request.totalFloors(), 0);
        String status = trimToNull(request.status());
        return new BuildingInput(code, request.buildingName().trim(), trimToNull(request.address()), totalFloors,
                status == null ? "ACTIVE" : status, trimToNull(request.description()));
    }

    private void ensureUpdated(int updated, String message) {
        if (updated == 0) {
            throw new BusinessException(404, message);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record BuildingRequest(
            String buildingCode,
            String buildingName,
            String address,
            Integer totalFloors,
            String status,
            String description
    ) {
    }

    private record BuildingInput(
            String code,
            String name,
            String address,
            Integer totalFloors,
            String status,
            String description
    ) {
    }
}
