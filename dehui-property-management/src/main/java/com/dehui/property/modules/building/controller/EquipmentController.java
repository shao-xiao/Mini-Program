package com.dehui.property.modules.building.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcMaps;
import com.dehui.property.common.JdbcPagination;
import com.dehui.property.common.PageResponse;
import com.dehui.property.common.WorkspaceOrderBy;
import com.dehui.property.modules.customfield.application.CustomFieldImportService;
import com.dehui.property.modules.customfield.application.CustomFieldImportService.ImportFieldDefinition;
import com.dehui.property.modules.customfield.application.CustomFieldProjectionService;
import com.dehui.property.modules.customfield.application.CustomFieldQueryCompiler.CompiledQuery;
import com.dehui.property.modules.customfield.application.CustomFieldQueryCompiler.NativeOrderPolicy;
import com.dehui.property.modules.customfield.application.CustomFieldQueryService;
import com.dehui.property.modules.customfield.domain.CustomFieldType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class EquipmentController {

    public static final String INSERT_EQUIPMENT_SQL = """
            INSERT INTO equipment (
                code,
                equipment_name,
                equipment_code,
                equipment_type,
                location,
                building_id,
                floor_id,
                room_id,
                location_type,
                manufacturer,
                model,
                serial_no,
                install_date,
                responsible_department,
                responsible_user_id,
                service_life_years,
                image_file_ids,
                attachment_file_ids,
                status,
                created_by,
                updated_by,
                remark
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String EQUIPMENT_SELECT_SQL = """
            SELECT e.id,
                   e.code,
                   e.equipment_name AS equipmentName,
                   e.equipment_code AS equipmentCode,
                   e.equipment_type AS equipmentType,
                   e.location,
                   e.building_id AS buildingId,
                   building_record.name AS buildingName,
                   e.floor_id AS floorId,
                   floor_record.name AS floorName,
                   e.room_id AS roomId,
                   room_record.room_no AS roomCode,
                   e.location_type AS locationType,
                   e.status,
                   e.manufacturer,
                   e.model,
                   e.serial_no AS serialNo,
                   e.install_date AS installDate,
                   e.responsible_department AS responsibleDepartment,
                   e.responsible_user_id AS responsibleUserId,
                   responsible_user.real_name AS responsibleUserName,
                   e.service_life_years AS serviceLifeYears,
                   e.image_file_ids AS imageFileIds,
                   e.attachment_file_ids AS attachmentFileIds,
                   e.remark,
                   e.created_at AS createdAt,
                   e.updated_at AS updatedAt
            FROM equipment e
            LEFT JOIN building building_record ON building_record.id = e.building_id AND building_record.deleted = 0
            LEFT JOIN building_floor floor_record ON floor_record.id = e.floor_id AND floor_record.deleted = 0
            LEFT JOIN building_room room_record ON room_record.id = e.room_id AND room_record.deleted = 0
            LEFT JOIN sys_user responsible_user ON responsible_user.id = e.responsible_user_id AND responsible_user.deleted = 0
            WHERE e.deleted = 0
            """;

    private static final String EQUIPMENT_COUNT_SQL = """
            SELECT COUNT(*)
            FROM equipment e
            WHERE e.deleted = 0
            """;

    private static final Set<String> ALLOWED_STATUS = Set.of("NORMAL", "FAULT", "MAINTENANCE", "DISABLED");
    private static final Set<String> ALLOWED_LOCATION_TYPES = Set.of("BUILDING", "FLOOR", "ROOM", "PUBLIC_AREA");
    private static final List<ImportFieldDefinition> IMPORT_CUSTOM_FIELDS = List.of(
            new ImportFieldDefinition("custom_quantity", "数量", CustomFieldType.NUMBER,
                    "设备导入数量", 10, true, false),
            new ImportFieldDefinition("custom_manufacturer_contact", "厂家联系方式", CustomFieldType.PHONE,
                    "设备导入厂家联系方式", 20, true, false),
            new ImportFieldDefinition("custom_warranty_period", "质保期", CustomFieldType.TEXT,
                    "设备导入质保期", 30, true, false),
            new ImportFieldDefinition("custom_source_sequence", "原表序号", CustomFieldType.TEXT,
                    "设备导入原表序号", 40, true, false)
    );
    private static final Map<String, String> ORDERABLE_FIELDS = Map.ofEntries(
            Map.entry("equipmentName", "e.equipment_name"),
            Map.entry("equipmentCode", "e.equipment_code"),
            Map.entry("equipmentType", "e.equipment_type"),
            Map.entry("location", "e.location"),
            Map.entry("status", "e.status"),
            Map.entry("manufacturer", "e.manufacturer"),
            Map.entry("model", "e.model"),
            Map.entry("installDate", "e.install_date"),
            Map.entry("updatedAt", "e.updated_at")
    );
    private static final Map<String, String> SYSTEM_TYPE_MAP = Map.ofEntries(
            Map.entry("电梯", "ELEVATOR"),
            Map.entry("通风", "HVAC"),
            Map.entry("新风", "HVAC"),
            Map.entry("空调", "HVAC"),
            Map.entry("消防", "FIRE"),
            Map.entry("配电", "POWER"),
            Map.entry("高压", "POWER"),
            Map.entry("强电", "POWER"),
            Map.entry("弱电", "SECURITY"),
            Map.entry("给排水", "WATER"),
            Map.entry("排水", "WATER"),
            Map.entry("机电", "OTHER"),
            Map.entry("光伏", "POWER")
    );

    private final JdbcTemplate jdbcTemplate;
    private final CustomFieldQueryService customFieldQueryService;
    private final CustomFieldProjectionService customFieldProjectionService;
    private final CustomFieldImportService customFieldImportService;

    public EquipmentController(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null, null, null);
    }

    public EquipmentController(JdbcTemplate jdbcTemplate, CustomFieldImportService customFieldImportService) {
        this(jdbcTemplate, null, null, customFieldImportService);
    }

    @GetMapping("/equipments")
    public ApiResponse<PageResponse<Map<String, Object>>> equipments(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String equipmentType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String customFilters,
            @RequestParam(required = false) String sorts,
            @RequestParam(required = false) String groupBy,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        StringBuilder selectSql = new StringBuilder(EQUIPMENT_SELECT_SQL);
        StringBuilder countSql = new StringBuilder(EQUIPMENT_COUNT_SQL);
        List<Object> args = new java.util.ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            String likeKeyword = "%" + keyword.trim() + "%";
            selectSql.append(" AND (e.equipment_name LIKE ? OR e.equipment_code LIKE ? OR e.location LIKE ? OR e.manufacturer LIKE ? OR e.model LIKE ?)");
            countSql.append(" AND (e.equipment_name LIKE ? OR e.equipment_code LIKE ? OR e.location LIKE ? OR e.manufacturer LIKE ? OR e.model LIKE ?)");
            for (int i = 0; i < 5; i++) {
                args.add(likeKeyword);
            }
        }

        if (equipmentType != null && !equipmentType.isBlank()) {
            selectSql.append(" AND e.equipment_type = ?");
            countSql.append(" AND e.equipment_type = ?");
            args.add(equipmentType.trim().toUpperCase());
        }

        if (status != null && !status.isBlank()) {
            String normalizedStatus = normalizeStatus(status);
            selectSql.append(" AND e.status = ?");
            countSql.append(" AND e.status = ?");
            args.add(normalizedStatus);
        }

        CompiledQuery customQuery = compileCustomQuery(customFilters, groupBy, sorts);
        if (customQuery != null && customQuery.hasPredicates()) {
            selectSql.append(" AND ").append(customQuery.predicateSql());
            countSql.append(" AND ").append(customQuery.predicateSql());
            args.addAll(customQuery.predicateArguments());
        }
        String orderBy = customQuery == null || !customQuery.hasOrderings()
                ? WorkspaceOrderBy.build(ORDERABLE_FIELDS, groupBy, sorts, null, null, "e.updated_at", "e.id")
                : String.join(", ", customQuery.orderExpressions());
        PageResponse<Map<String, Object>> result = JdbcPagination.query(
                jdbcTemplate,
                selectSql + " ORDER BY " + orderBy,
                countSql.toString(),
                args,
                customQuery == null ? List.of() : customQuery.orderArguments(),
                page,
                pageSize
        );
        if (customFieldProjectionService != null) {
            result = customFieldProjectionService.attach("equipment", result);
        }
        return ApiResponse.success(result);
    }

    public ApiResponse<PageResponse<Map<String, Object>>> equipments(
            String keyword,
            String equipmentType,
            String status,
            String sorts,
            String groupBy,
            Integer page,
            Integer pageSize) {
        return equipments(keyword, equipmentType, status, null, sorts, groupBy, page, pageSize);
    }

    public ApiResponse<PageResponse<Map<String, Object>>> equipments(
            String keyword,
            String equipmentType,
            String status,
            Integer page,
            Integer pageSize) {
        return equipments(keyword, equipmentType, status, null, null, null, page, pageSize);
    }

    private CompiledQuery compileCustomQuery(String customFilters, String groupBy, String sorts) {
        if (customFieldQueryService == null) return null;
        return customFieldQueryService.compile(
                "equipment",
                customFilters,
                groupBy,
                sorts,
                NativeOrderPolicy.of(ORDERABLE_FIELDS, null, null, "e.updated_at", "e.id")
        );
    }

    @PostMapping("/equipments")
    public ApiResponse<Void> createEquipment(@RequestBody Map<String, Object> body) {
        String equipmentName = JdbcMaps.requiredStr(body, "Equipment name is required", "equipmentName");
        String status = JdbcMaps.str(body, "status");
        EquipmentLocation standardLocation = equipmentLocation(body);
        try {
            jdbcTemplate.update(
                    INSERT_EQUIPMENT_SQL,
                    JdbcMaps.code("EQP"),
                    equipmentName,
                    JdbcMaps.str(body, "equipmentCode"),
                    JdbcMaps.strOr(body, "OTHER", "equipmentType"),
                    JdbcMaps.str(body, "location"),
                    standardLocation.buildingId(),
                    standardLocation.floorId(),
                    standardLocation.roomId(),
                    standardLocation.locationType(),
                    JdbcMaps.str(body, "manufacturer"),
                    JdbcMaps.str(body, "model"),
                    JdbcMaps.str(body, "serialNo"),
                    JdbcMaps.date(body, null, "installDate"),
                    JdbcMaps.str(body, "responsibleDepartment"),
                    JdbcMaps.longVal(body, "responsibleUserId"),
                    JdbcMaps.intVal(body, null, "serviceLifeYears"),
                    jsonValue(body.get("imageFileIds")),
                    jsonValue(body.get("attachmentFileIds")),
                    status == null ? "NORMAL" : normalizeStatus(status),
                    0L,
                    0L,
                    JdbcMaps.str(body, "remark")
            );
            return ApiResponse.success();
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(400, "Equipment code already exists");
        }
    }

    @GetMapping("/equipments/template")
    public ResponseEntity<byte[]> downloadImportTemplate() {
        String csv = "\uFEFF序号,设备/材料名称,规格型号,数量,安装位置,生产厂家,厂家联系方式,质保期,所属系统,备注,固定资产编码\n"
                + "1,排气扇EF02,\"风量250m3/h，功率32W\",10,备配电间,上虞清明风机有限公司,李明洋-18689442292,2025年6月18日-2027年6月18日,通风,,CZXXTF001007\n";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"equipment-import-template.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/equipments/import")
    public ApiResponse<Map<String, Object>> importEquipments(@RequestBody Map<String, Object> body) {
        Object rowsValue = body == null ? null : body.get("rows");
        if (!(rowsValue instanceof List<?> rows)) {
            throw new BusinessException(400, "Import rows are required");
        }

        requireCustomFieldImportService().ensureDefinitions("equipment", IMPORT_CUSTOM_FIELDS);
        int totalCount = rows.size();
        int insertedCount = 0;
        int updatedCount = 0;
        int restoredCount = 0;
        int skippedCount = 0;
        int failureCount = 0;
        java.util.List<Map<String, Object>> errors = new java.util.ArrayList<>();

        for (int index = 0; index < rows.size(); index++) {
            Object value = rows.get(index);
            if (!(value instanceof Map<?, ?> rawRow)) {
                failureCount++;
                addImportError(errors, index, "", "Invalid row");
                continue;
            }
            Map<String, Object> row = normalizeRow(rawRow);
            String equipmentName = JdbcMaps.str(row, "equipmentName", "设备/材料名称", "设备名称");
            String rawEquipmentCode = JdbcMaps.str(row, "equipmentCode", "assetCode", "固定资产编码", "设备编号");
            if (equipmentName == null) {
                failureCount++;
                addImportError(errors, index, rawEquipmentCode, "设备名称为空");
                continue;
            }

            String equipmentCode = rawEquipmentCode == null ? generateImportEquipmentCode() : rawEquipmentCode;

            try {
                String systemName = JdbcMaps.str(row, "systemName", "所属系统");
                String remark = importRemark(JdbcMaps.str(row, "remark", "备注"), systemName);
                Map<String, Object> existingEquipment = findEquipmentByCodeIncludeDeleted(equipmentCode);
                if (existingEquipment != null) {
                    Long existingEquipmentId = ((Number) existingEquipment.get("id")).longValue();
                    supplementEquipment(existingEquipmentId, row, equipmentName, systemName, remark);
                    saveImportCustomFields(existingEquipmentId, row);
                    if (isDeleted(existingEquipment)) {
                        restoredCount++;
                    } else {
                        updatedCount++;
                    }
                    continue;
                }

                jdbcTemplate.update(
                        INSERT_EQUIPMENT_SQL,
                        JdbcMaps.code("EQP"),
                        equipmentName,
                        equipmentCode,
                        equipmentTypeFromSystem(systemName),
                        JdbcMaps.str(row, "location", "安装位置"),
                        null,
                        null,
                        null,
                        "PUBLIC_AREA",
                        JdbcMaps.str(row, "manufacturer", "生产厂家"),
                        JdbcMaps.str(row, "model", "规格型号"),
                        null,
                        JdbcMaps.date(row, null, "installDate", "安装日期"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        "NORMAL",
                        0L,
                        0L,
                        remark
                );
                Long equipmentId = latestEquipmentId(equipmentCode, equipmentName);
                if (equipmentId != null) {
                    saveImportCustomFields(equipmentId, row);
                }
                insertedCount++;
            } catch (DuplicateKeyException exception) {
                Map<String, Object> existingEquipment = findEquipmentByCodeIncludeDeleted(equipmentCode);
                if (existingEquipment != null) {
                    Long existingEquipmentId = ((Number) existingEquipment.get("id")).longValue();
                    String systemName = JdbcMaps.str(row, "systemName", "所属系统");
                    String remark = importRemark(JdbcMaps.str(row, "remark", "备注"), systemName);
                    supplementEquipment(existingEquipmentId, row, equipmentName, systemName, remark);
                    saveImportCustomFields(existingEquipmentId, row);
                    if (isDeleted(existingEquipment)) {
                        restoredCount++;
                    } else {
                        updatedCount++;
                    }
                } else {
                    failureCount++;
                    addImportError(errors, index, equipmentCode, "设备编号重复但未找到可更新记录: " + exception.getMessage());
                }
            } catch (Exception exception) {
                failureCount++;
                addImportError(errors, index, equipmentCode, exception.getMessage() == null ? "Import failed" : exception.getMessage());
            }
        }

        return ApiResponse.success(Map.of(
                "totalCount", totalCount,
                "insertedCount", insertedCount,
                "successCount", insertedCount,
                "updatedCount", updatedCount,
                "restoredCount", restoredCount,
                "skippedCount", skippedCount,
                "failureCount", failureCount,
                "errors", errors,
                "failures", errors
        ));
    }

    @PutMapping("/equipments/{id}")
    public ApiResponse<Void> updateEquipment(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String equipmentName = JdbcMaps.requiredStr(body, "Equipment name is required", "equipmentName");
        String status = JdbcMaps.str(body, "status");
        if (status != null) {
            status = normalizeStatus(status);
        }
        EquipmentLocation standardLocation = equipmentLocation(body);
        try {
            int updated = jdbcTemplate.update(
                    """
                    UPDATE equipment
                    SET equipment_name = ?,
                        equipment_code = ?,
                        equipment_type = ?,
                        location = ?,
                        building_id = ?,
                        floor_id = ?,
                        room_id = ?,
                        location_type = ?,
                        manufacturer = ?,
                        model = ?,
                        serial_no = ?,
                        install_date = ?,
                        responsible_department = ?,
                        responsible_user_id = ?,
                        service_life_years = ?,
                        image_file_ids = ?,
                        attachment_file_ids = ?,
                        status = COALESCE(?, status),
                        updated_by = ?,
                        remark = ?
                    WHERE id = ? AND deleted = 0
                    """,
                    equipmentName,
                    JdbcMaps.str(body, "equipmentCode"),
                    JdbcMaps.strOr(body, "OTHER", "equipmentType"),
                    JdbcMaps.str(body, "location"),
                    standardLocation.buildingId(),
                    standardLocation.floorId(),
                    standardLocation.roomId(),
                    standardLocation.locationType(),
                    JdbcMaps.str(body, "manufacturer"),
                    JdbcMaps.str(body, "model"),
                    JdbcMaps.str(body, "serialNo"),
                    JdbcMaps.date(body, null, "installDate"),
                    JdbcMaps.str(body, "responsibleDepartment"),
                    JdbcMaps.longVal(body, "responsibleUserId"),
                    JdbcMaps.intVal(body, null, "serviceLifeYears"),
                    jsonValue(body.get("imageFileIds")),
                    jsonValue(body.get("attachmentFileIds")),
                    status,
                    0L,
                    JdbcMaps.str(body, "remark"),
                    id
            );
            ensureUpdated(updated, "Equipment not found");
            return ApiResponse.success();
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(400, "Equipment code already exists");
        }
    }

    @PatchMapping("/equipments/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        int updated = jdbcTemplate.update(
                "UPDATE equipment SET status = ?, updated_by = ? WHERE id = ? AND deleted = 0",
                normalizeStatus(status),
                0L,
                id
        );
        ensureUpdated(updated, "Equipment not found");
        return ApiResponse.success();
    }

    @DeleteMapping("/equipments/{id}")
    public ApiResponse<Void> deleteEquipment(@PathVariable Long id) {
        int updated = jdbcTemplate.update(
                "UPDATE equipment SET deleted = 1, updated_by = ? WHERE id = ? AND deleted = 0",
                0L,
                id
        );
        ensureUpdated(updated, "Equipment not found");
        return ApiResponse.success();
    }

    private String normalizeStatus(String status) {
        String normalized = status == null ? null : status.trim().toUpperCase();
        if (normalized == null || normalized.isBlank() || !ALLOWED_STATUS.contains(normalized)) {
            throw new BusinessException(400, "Equipment status is invalid");
        }
        return normalized;
    }

    private EquipmentLocation equipmentLocation(Map<String, Object> body) {
        Long buildingId = JdbcMaps.longVal(body, "buildingId");
        Long floorId = JdbcMaps.longVal(body, "floorId");
        Long roomId = JdbcMaps.longVal(body, "roomId");
        String locationType = JdbcMaps.strOr(
                body,
                roomId != null ? "ROOM" : floorId != null ? "FLOOR" : buildingId != null ? "BUILDING" : "PUBLIC_AREA",
                "locationType"
        ).trim().toUpperCase(java.util.Locale.ROOT);
        if (!ALLOWED_LOCATION_TYPES.contains(locationType)) {
            throw new BusinessException(400, "Equipment location type is invalid");
        }
        if (floorId != null && buildingId == null) {
            throw new BusinessException(400, "A floor location must select its building");
        }
        if (roomId != null && (buildingId == null || floorId == null)) {
            throw new BusinessException(400, "A room location must select its building and floor");
        }
        if (buildingId != null) {
            requireReference("building", buildingId, null, null, "Building does not exist or is disabled");
        }
        if (floorId != null) {
            requireReference("building_floor", floorId, buildingId, null, "Floor does not belong to the selected building");
        }
        if (roomId != null) {
            requireReference("building_room", roomId, buildingId, floorId, "Room does not belong to the selected floor");
        }
        return new EquipmentLocation(buildingId, floorId, roomId, locationType);
    }

    private void requireReference(String table, Long id, Long buildingId, Long floorId, String message) {
        String sql = switch (table) {
            case "building" -> "SELECT COUNT(*) FROM building WHERE id = ? AND deleted = 0 AND status IN ('ACTIVE', 'ENABLED')";
            case "building_floor" -> "SELECT COUNT(*) FROM building_floor WHERE id = ? AND building_id = ? AND deleted = 0 AND status IN ('ACTIVE', 'ENABLED')";
            case "building_room" -> "SELECT COUNT(*) FROM building_room WHERE id = ? AND building_id = ? AND floor_id = ? AND deleted = 0";
            default -> throw new IllegalArgumentException("Unsupported equipment reference");
        };
        Long count = switch (table) {
            case "building" -> jdbcTemplate.queryForObject(sql, Long.class, id);
            case "building_floor" -> jdbcTemplate.queryForObject(sql, Long.class, id, buildingId);
            default -> jdbcTemplate.queryForObject(sql, Long.class, id, buildingId, floorId);
        };
        if (count == null || count == 0) {
            throw new BusinessException(400, message);
        }
    }

    private String jsonValue(Object value) {
        if (value == null) return null;
        if (value instanceof String text) return text;
        try {
            return new ObjectMapper().writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new BusinessException(400, "Equipment file list is invalid");
        }
    }

    private void ensureUpdated(int updated, String message) {
        if (updated == 0) {
            throw new BusinessException(404, message);
        }
    }

    private Map<String, Object> normalizeRow(Map<?, ?> rawRow) {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        rawRow.forEach((key, value) -> {
            if (key != null) {
                row.put(String.valueOf(key).trim().replace("\uFEFF", ""), value);
            }
        });
        return row;
    }

    private Map<String, Object> findEquipmentByCodeIncludeDeleted(String equipmentCode) {
        if (equipmentCode == null || equipmentCode.isBlank()) {
            return null;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, deleted FROM equipment WHERE equipment_code = ? ORDER BY deleted ASC, id DESC LIMIT 1",
                equipmentCode
        );
        if (rows.isEmpty()) {
            return null;
        }
        return rows.getFirst();
    }

    private boolean isDeleted(Map<String, Object> equipment) {
        Object value = equipment.get("deleted");
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return value != null && !"0".equals(String.valueOf(value).trim());
    }

    private String generateImportEquipmentCode() {
        return JdbcMaps.code("EQP-IMP");
    }

    private void addImportError(java.util.List<Map<String, Object>> errors, int index, String equipmentCode, String message) {
        if (errors.size() >= 20) {
            return;
        }
        errors.add(Map.of(
                "rowIndex", index + 1,
                "equipmentCode", equipmentCode == null ? "" : equipmentCode,
                "message", message == null ? "Import failed" : message
        ));
    }

    private String equipmentTypeFromSystem(String systemName) {
        if (systemName == null || systemName.isBlank()) {
            return "OTHER";
        }
        String normalized = systemName.trim();
        for (Map.Entry<String, String> entry : SYSTEM_TYPE_MAP.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "OTHER";
    }

    private String importRemark(String remark, String systemName) {
        if (systemName == null || systemName.isBlank()) {
            return remark;
        }
        String systemRemark = "所属系统: " + systemName.trim();
        if (remark == null || remark.isBlank()) {
            return systemRemark;
        }
        return remark.trim() + "；" + systemRemark;
    }

    private void supplementEquipment(Long equipmentId, Map<String, Object> row, String equipmentName, String systemName, String remark) {
        jdbcTemplate.update(
                """
                UPDATE equipment
                SET deleted = 0,
                    equipment_name = ?,
                    equipment_type = ?,
                    status = CASE
                        WHEN status IS NULL OR status = '' THEN 'NORMAL'
                        ELSE status
                    END,
                    location = COALESCE(NULLIF(?, ''), location),
                    manufacturer = COALESCE(NULLIF(?, ''), manufacturer),
                    model = COALESCE(NULLIF(?, ''), model),
                    install_date = COALESCE(?, install_date),
                    remark = CASE
                        WHEN ? IS NULL OR ? = '' THEN remark
                        WHEN remark IS NULL OR remark = '' THEN ?
                        WHEN remark LIKE CONCAT('%', ?, '%') THEN remark
                        ELSE CONCAT(remark, '；', ?)
                    END,
                    updated_by = ?
                WHERE id = ?
                """,
                equipmentName,
                equipmentTypeFromSystem(systemName),
                JdbcMaps.str(row, "location", "安装位置"),
                JdbcMaps.str(row, "manufacturer", "生产厂家"),
                JdbcMaps.str(row, "model", "规格型号"),
                JdbcMaps.date(row, null, "installDate", "安装日期"),
                remark,
                remark,
                remark,
                remark,
                remark,
                0L,
                equipmentId
        );
    }

    private Long latestEquipmentId(String equipmentCode, String equipmentName) {
        List<Map<String, Object>> rows;
        if (equipmentCode != null) {
            rows = jdbcTemplate.queryForList(
                    "SELECT id FROM equipment WHERE equipment_code = ? AND deleted = 0 ORDER BY id DESC LIMIT 1",
                    equipmentCode
            );
        } else {
            rows = jdbcTemplate.queryForList(
                    "SELECT id FROM equipment WHERE equipment_name = ? AND deleted = 0 ORDER BY id DESC LIMIT 1",
                    equipmentName
            );
        }
        if (rows.isEmpty()) {
            return null;
        }
        return ((Number) rows.getFirst().get("id")).longValue();
    }

    private void saveImportCustomFields(Long equipmentId, Map<String, Object> row) {
        Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("custom_quantity", JdbcMaps.str(row, "quantity", "数量"));
        values.put("custom_manufacturer_contact", JdbcMaps.str(row, "manufacturerContact", "厂家联系方式"));
        values.put("custom_warranty_period", JdbcMaps.str(row, "warrantyPeriod", "质保期"));
        values.put("custom_source_sequence", JdbcMaps.str(row, "sequence", "序号"));
        requireCustomFieldImportService().saveImportedRow("equipment", equipmentId, row, values);
    }

    private CustomFieldImportService requireCustomFieldImportService() {
        if (customFieldImportService == null) {
            throw new IllegalStateException("Custom-field import service is unavailable");
        }
        return customFieldImportService;
    }

    private record EquipmentLocation(Long buildingId, Long floorId, Long roomId, String locationType) {
    }
}
