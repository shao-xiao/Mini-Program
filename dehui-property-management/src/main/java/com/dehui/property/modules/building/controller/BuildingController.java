package com.dehui.property.modules.building.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcPagination;
import com.dehui.property.common.JdbcMaps;
import com.dehui.property.common.PageResponse;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
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

    private final JdbcTemplate jdbcTemplate;

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
                    INSERT INTO building_floor (code, building_id, floor_no, name, status, created_by, updated_by, remark)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    JdbcMaps.code("FLR"),
                    buildingId,
                    floorNo,
                    name,
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
                SET building_id = ?, floor_no = ?, name = ?, status = ?, updated_by = ?, remark = ?
                WHERE id = ? AND deleted = 0
                """,
                buildingId,
                floorNo,
                JdbcMaps.strOr(body, floorNo + "F", "floorName", "name"),
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
                       r.usage_type AS roomType,
                       r.rent_status AS status,
                       r.remark AS description
                FROM building_room r
                LEFT JOIN building b ON b.id = r.building_id
                LEFT JOIN building_floor f ON f.id = r.floor_id
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
        sql.append(" ORDER BY r.room_no ASC, r.id ASC");
        return ApiResponse.success(JdbcPagination.query(jdbcTemplate, sql.toString(), countSql.toString(), args, page, pageSize));
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
    public ApiResponse<Void> checkoutRoom(@PathVariable Long id) {
        int updated = jdbcTemplate.update("UPDATE building_room SET rent_status = 'VACANT', updated_by = ? WHERE id = ? AND deleted = 0", 0L, id);
        ensureUpdated(updated, "Room not found");
        return ApiResponse.success();
    }

    @GetMapping("/mobile/building/summary")
    public ApiResponse<Void> mobileSummary() {
        throw BusinessException.notImplemented("mobile building summary");
    }

    private void upsertRoom(Long id, Map<String, Object> body) {
        Long buildingId = JdbcMaps.requiredLong(body, "Building is required", "buildingId", "building_id");
        Long floorId = JdbcMaps.requiredLong(body, "Floor is required", "floorId", "floor_id");
        String roomNo = JdbcMaps.requiredStr(body, "Room number is required", "roomNumber", "roomNo", "room_no");
        String rentStatus = JdbcMaps.strOr(body, "VACANT", "status", "rentStatus");
        if ("AVAILABLE".equals(rentStatus)) {
            rentStatus = "VACANT";
        }
        if (id == null) {
            jdbcTemplate.update(
                    """
                    INSERT INTO building_room (code, building_id, floor_id, room_no, area, usage_type, rent_status, status, created_by, updated_by, remark)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    JdbcMaps.code("ROM"),
                    buildingId,
                    floorId,
                    roomNo,
                    JdbcMaps.decimal(body, java.math.BigDecimal.ZERO, "area"),
                    JdbcMaps.strOr(body, "OFFICE", "roomType", "usageType"),
                    rentStatus,
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
                SET building_id = ?, floor_id = ?, room_no = ?, area = ?, usage_type = ?, rent_status = ?, updated_by = ?, remark = ?
                WHERE id = ? AND deleted = 0
                """,
                buildingId,
                floorId,
                roomNo,
                JdbcMaps.decimal(body, java.math.BigDecimal.ZERO, "area"),
                JdbcMaps.strOr(body, "OFFICE", "roomType", "usageType"),
                rentStatus,
                0L,
                JdbcMaps.str(body, "description", "remark", "roomName"),
                id
        );
        ensureUpdated(updated, "Room not found");
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
