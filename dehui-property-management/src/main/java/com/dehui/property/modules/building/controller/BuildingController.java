package com.dehui.property.modules.building.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
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
            jdbcTemplate.update(
                    INSERT_BUILDING_SQL,
                    input.code(),
                    input.name(),
                    input.address(),
                    input.totalFloors(),
                    input.status(),
                    0L,
                    0L,
                    input.description()
            );
            return ApiResponse.success();
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(400, "Building code or name already exists");
        }
    }

    @PutMapping("/buildings/{id}")
    public ApiResponse<Void> updateBuilding(@PathVariable Long id, @RequestBody BuildingRequest request) {
        BuildingInput input = normalize(request, id);
        try {
            int updated = jdbcTemplate.update(
                    UPDATE_BUILDING_SQL,
                    input.code(),
                    input.name(),
                    input.address(),
                    input.totalFloors(),
                    input.status(),
                    0L,
                    input.description(),
                    id
            );
            if (updated == 0) {
                throw new BusinessException(404, "Building not found");
            }
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
        if (updated == 0) {
            throw new BusinessException(404, "Building not found");
        }
        return ApiResponse.success();
    }

    @GetMapping("/floors")
    public ApiResponse<Void> floors() {
        throw BusinessException.notImplemented("floor");
    }

    @GetMapping("/rooms")
    public ApiResponse<Void> rooms() {
        throw BusinessException.notImplemented("room");
    }

    @GetMapping("/mobile/building/summary")
    public ApiResponse<Void> mobileSummary() {
        throw BusinessException.notImplemented("mobile building summary");
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
        return new BuildingInput(
                code,
                request.buildingName().trim(),
                trimToNull(request.address()),
                totalFloors,
                status == null ? "ACTIVE" : status,
                trimToNull(request.description())
        );
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
