package com.dehui.property.modules.energy.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcMaps;
import com.dehui.property.common.JdbcPagination;
import com.dehui.property.common.PageResponse;
import com.dehui.property.common.WorkspaceOrderBy;
import com.dehui.property.modules.asset.application.AssetChargeBasisService;
import com.dehui.property.modules.customfield.application.CustomFieldImportService;
import com.dehui.property.modules.customfield.application.CustomFieldImportService.ImportFieldHeader;
import com.dehui.property.modules.customfield.application.CustomFieldProjectionService;
import com.dehui.property.modules.customfield.application.CustomFieldQueryCompiler.CompiledQuery;
import com.dehui.property.modules.customfield.application.CustomFieldQueryCompiler.NativeOrderPolicy;
import com.dehui.property.modules.customfield.application.CustomFieldQueryService;
import com.dehui.property.security.AuthInterceptor;
import com.dehui.property.security.AuthPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EnergyController {

    public static final String INSERT_METER_SQL = """
            INSERT INTO energy_meter (
                code,
                meter_no,
                meter_name,
                meter_type,
                building_id,
                floor_id,
                room_id,
                location_description,
                tenant_id,
                unit,
                multiplier,
                initial_reading,
                commissioning_date,
                status,
                created_by,
                updated_by,
                remark
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String METER_SELECT_SQL = """
            SELECT m.id,
                   m.code,
                   m.meter_no AS meterNo,
                   m.meter_name AS meterName,
                   m.meter_type AS meterType,
                   m.building_id AS buildingId,
                   b.name AS buildingName,
                   m.floor_id AS floorId,
                   f.name AS floorName,
                   m.room_id AS roomId,
                   r.room_no AS roomName,
                   m.location_description AS locationDescription,
                   m.tenant_id AS tenantId,
                   t.name AS tenantName,
                   m.unit,
                   m.multiplier,
                   m.initial_reading AS initialReading,
                   m.commissioning_date AS commissioningDate,
                   m.status,
                   m.remark,
                   m.created_at AS createdAt,
                   m.updated_at AS updatedAt
            FROM energy_meter m
            LEFT JOIN building b ON b.id = m.building_id
            LEFT JOIN building_floor f ON f.id = m.floor_id
            LEFT JOIN building_room r ON r.id = m.room_id
            LEFT JOIN tenant t ON t.id = m.tenant_id
            WHERE m.deleted = 0
            """;

    private static final String METER_COUNT_SQL = """
            SELECT COUNT(*)
            FROM energy_meter m
            LEFT JOIN building b ON b.id = m.building_id
            LEFT JOIN building_floor f ON f.id = m.floor_id
            LEFT JOIN building_room r ON r.id = m.room_id
            LEFT JOIN tenant t ON t.id = m.tenant_id
            WHERE m.deleted = 0
            """;

    private static final String READING_SELECT_SQL = """
            SELECT er.id,
                   er.code,
                   er.meter_id AS meterId,
                   m.tenant_id AS tenantId,
                   m.room_id AS roomId,
                   m.meter_no AS meterNo,
                   m.meter_name AS meterName,
                   m.meter_type AS meterType,
                   m.unit,
                   b.name AS buildingName,
                   f.name AS floorName,
                   r.room_no AS roomName,
                   t.name AS tenantName,
                   er.period_month AS periodMonth,
                   er.reading_date AS readingDate,
                   er.previous_reading AS previousReading,
                   er.current_reading AS currentReading,
                   er.usage_amount AS usageAmount,
                   er.multiplier,
                   er.unit_price AS unitPrice,
                   er.settlement_amount AS settlementAmount,
                   er.operator_name AS operatorName,
                   er.reading_source AS source,
                   er.attachment_file_id AS attachmentFileId,
                   er.reset_reason AS resetReason,
                   er.bill_status AS billStatus,
                   er.charge_basis_id AS chargeBasisId,
                   er.bill_id AS billId,
                   linked_bill.code AS billNumber,
                   er.abnormal_flag AS abnormalFlag,
                   er.abnormal_reason AS abnormalReason,
                   er.abnormal_status AS abnormalStatus,
                   er.status,
                   er.remark,
                   er.created_at AS createdTime,
                   er.updated_at AS updatedAt
            FROM energy_reading er
            JOIN energy_meter m ON m.id = er.meter_id AND m.deleted = 0
            LEFT JOIN building b ON b.id = m.building_id
            LEFT JOIN building_floor f ON f.id = m.floor_id
            LEFT JOIN building_room r ON r.id = m.room_id
            LEFT JOIN tenant t ON t.id = m.tenant_id
            LEFT JOIN bill linked_bill ON linked_bill.id = er.bill_id AND linked_bill.deleted = 0
            WHERE er.deleted = 0
            """;

    private static final String READING_COUNT_SQL = """
            SELECT COUNT(*)
            FROM energy_reading er
            JOIN energy_meter m ON m.id = er.meter_id AND m.deleted = 0
            LEFT JOIN building b ON b.id = m.building_id
            LEFT JOIN building_floor f ON f.id = m.floor_id
            LEFT JOIN building_room r ON r.id = m.room_id
            LEFT JOIN tenant t ON t.id = m.tenant_id
            WHERE er.deleted = 0
            """;

    private static final Set<String> METER_TYPES = Set.of("ELECTRIC", "WATER", "GAS");
    private static final Set<String> METER_STATUSES = Set.of("ENABLED", "DISABLED");
    private static final Set<String> METER_SUBJECT_TYPES = Set.of("PROPERTY", "TENANT", "ROOM", "PUBLIC_AREA");
    private static final Set<String> BILL_STATUSES = Set.of("NOT_GENERATED", "BASIS_CONFIRMED", "GENERATED", "POSTED");
    private static final Set<String> ANOMALY_STATUSES = Set.of("PENDING", "CONFIRMED", "IGNORED", "RESOLVED");
    private static final List<String> PRICE_SETTING_TYPES = List.of("ELECTRIC", "WATER", "GAS");
    private static final Map<String, String> METER_ORDERABLE_FIELDS = Map.ofEntries(
            Map.entry("meterNo", "m.meter_no"),
            Map.entry("meterName", "m.meter_name"),
            Map.entry("meterType", "m.meter_type"),
            Map.entry("buildingName", "b.name"),
            Map.entry("roomName", "r.room_no"),
            Map.entry("tenantName", "t.name"),
            Map.entry("multiplier", "m.multiplier"),
            Map.entry("status", "m.status"),
            Map.entry("updatedAt", "m.updated_at")
    );
    private static final Map<String, String> READING_ORDERABLE_FIELDS = Map.ofEntries(
            Map.entry("meterType", "m.meter_type"),
            Map.entry("periodMonth", "er.period_month"),
            Map.entry("meterNo", "m.meter_no"),
            Map.entry("buildingName", "b.name"),
            Map.entry("roomName", "r.room_no"),
            Map.entry("tenantName", "t.name"),
            Map.entry("readingDate", "er.reading_date"),
            Map.entry("previousReading", "er.previous_reading"),
            Map.entry("currentReading", "er.current_reading"),
            Map.entry("usageAmount", "er.usage_amount"),
            Map.entry("operatorName", "er.operator_name"),
            Map.entry("anomalyState", "CASE WHEN er.abnormal_flag = 0 THEN 'NORMAL' ELSE COALESCE(er.abnormal_status, 'PENDING') END"),
            Map.entry("billStatus", "er.bill_status"),
            Map.entry("unitPrice", "er.unit_price"),
            Map.entry("settlementAmount", "er.settlement_amount"),
            Map.entry("updatedAt", "er.updated_at")
    );

    private final JdbcTemplate jdbcTemplate;
    private final CustomFieldQueryService customFieldQueryService;
    private final CustomFieldProjectionService customFieldProjectionService;
    private final CustomFieldImportService customFieldImportService;
    private final AssetChargeBasisService assetChargeBasisService;

    public EnergyController(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null, null, null, new AssetChargeBasisService(jdbcTemplate, new ObjectMapper()));
    }

    public EnergyController(JdbcTemplate jdbcTemplate, CustomFieldImportService customFieldImportService) {
        this(
                jdbcTemplate,
                null,
                null,
                customFieldImportService,
                new AssetChargeBasisService(jdbcTemplate, new ObjectMapper())
        );
    }

    @GetMapping("/energy/meters")
    public ApiResponse<PageResponse<Map<String, Object>>> meters(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String meterType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long floorId,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String tenantScope,
            @RequestParam(required = false) String customFilters,
            @RequestParam(required = false) String sorts,
            @RequestParam(required = false) String groupBy,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        QueryParts query = meterQuery(keyword, meterType, status, buildingId, floorId, roomId, tenantId, tenantScope);
        CompiledQuery customQuery = compileCustomQuery(
                "energy-meters", customFilters, groupBy, sorts,
                METER_ORDERABLE_FIELDS, "m.updated_at", "m.id"
        );
        String where = query.where();
        List<Object> args = new ArrayList<>(query.args());
        if (customQuery != null && customQuery.hasPredicates()) {
            where += " AND " + customQuery.predicateSql();
            args.addAll(customQuery.predicateArguments());
        }
        String orderBy = customQuery == null || !customQuery.hasOrderings()
                ? WorkspaceOrderBy.build(
                        METER_ORDERABLE_FIELDS, groupBy, sorts, null, null,
                        "m.updated_at", "m.id"
                )
                : String.join(", ", customQuery.orderExpressions());
        PageResponse<Map<String, Object>> result = JdbcPagination.query(
                jdbcTemplate,
                METER_SELECT_SQL + where + " ORDER BY " + orderBy,
                METER_COUNT_SQL + where,
                args,
                customQuery == null ? List.of() : customQuery.orderArguments(),
                page,
                pageSize
        );
        if (customFieldProjectionService != null) {
            result = customFieldProjectionService.attach("energy-meters", result);
        }
        return ApiResponse.success(result);
    }

    public ApiResponse<PageResponse<Map<String, Object>>> meters(
            String keyword,
            String meterType,
            String status,
            Long buildingId,
            Long floorId,
            Long roomId,
            Long tenantId,
            String customFilters,
            String sorts,
            String groupBy,
            Integer page,
            Integer pageSize) {
        return meters(
                keyword, meterType, status, buildingId, floorId, roomId, tenantId,
                null, customFilters, sorts, groupBy, page, pageSize
        );
    }

    @Autowired
    public EnergyController(
            JdbcTemplate jdbcTemplate,
            CustomFieldQueryService customFieldQueryService,
            CustomFieldProjectionService customFieldProjectionService,
            CustomFieldImportService customFieldImportService) {
        this(
                jdbcTemplate,
                customFieldQueryService,
                customFieldProjectionService,
                customFieldImportService,
                new AssetChargeBasisService(jdbcTemplate, new ObjectMapper())
        );
    }

    public EnergyController(
            JdbcTemplate jdbcTemplate,
            CustomFieldQueryService customFieldQueryService,
            CustomFieldProjectionService customFieldProjectionService,
            CustomFieldImportService customFieldImportService,
            AssetChargeBasisService assetChargeBasisService) {
        this.jdbcTemplate = jdbcTemplate;
        this.customFieldQueryService = customFieldQueryService;
        this.customFieldProjectionService = customFieldProjectionService;
        this.customFieldImportService = customFieldImportService;
        this.assetChargeBasisService = assetChargeBasisService;
    }

    public ApiResponse<PageResponse<Map<String, Object>>> meters(
            String keyword,
            String meterType,
            String status,
            Long buildingId,
            Long floorId,
            Long roomId,
            Long tenantId,
            String sorts,
            String groupBy,
            Integer page,
            Integer pageSize) {
        return meters(
                keyword, meterType, status, buildingId, floorId, roomId, tenantId,
                null, null, sorts, groupBy, page, pageSize
        );
    }

    public ApiResponse<PageResponse<Map<String, Object>>> meters(
            String keyword,
            String meterType,
            String status,
            Long buildingId,
            Long floorId,
            Long roomId,
            Long tenantId,
            Integer page,
            Integer pageSize) {
        return meters(keyword, meterType, status, buildingId, floorId, roomId, tenantId, null, null, null, null, page, pageSize);
    }

    @GetMapping("/energy/meters/options")
    public ApiResponse<PageResponse<Map<String, Object>>> meterOptions(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String meterType,
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long floorId,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String tenantScope,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        int optionPageSize = pageSize == null ? 20 : Math.min(pageSize, 50);
        QueryParts query = meterQuery(keyword, meterType, "ENABLED", buildingId, floorId, roomId, tenantId, tenantScope);
        return ApiResponse.success(JdbcPagination.query(
                jdbcTemplate,
                METER_SELECT_SQL + query.where() + " ORDER BY m.meter_no ASC, m.id ASC",
                METER_COUNT_SQL + query.where(),
                query.args(),
                page,
                optionPageSize
        ));
    }

    @PostMapping("/energy/meters")
    @Transactional
    public ApiResponse<Void> createMeter(@RequestBody Map<String, Object> body) {
        String meterNo = JdbcMaps.requiredStr(body, "表具编号不能为空", "meterNo", "meterCode");
        String meterType = normalizeMeterType(JdbcMaps.strOr(body, "ELECTRIC", "meterType"));
        String status = normalizeMeterStatus(JdbcMaps.strOr(body, "ENABLED", "status"));
        MeterLocation location = meterLocation(body);
        InitialMeterBinding initialBinding = initialMeterBinding(body, location);
        BigDecimal initialReading = nonNegativeDecimal(body, BigDecimal.ZERO, "initialReading");
        String meterCode = JdbcMaps.code("MTR");
        try {
            jdbcTemplate.update(
                    INSERT_METER_SQL,
                    meterCode,
                    meterNo,
                    JdbcMaps.strOr(body, meterNo, "meterName"),
                    meterType,
                    location.buildingId(),
                    location.floorId(),
                    location.roomId(),
                    JdbcMaps.str(body, "locationDescription"),
                    JdbcMaps.strOr(body, defaultUnit(meterType), "unit"),
                    positiveDecimal(body, BigDecimal.ONE, "multiplier"),
                    initialReading,
                    JdbcMaps.date(body, null, "commissioningDate"),
                    status,
                    0L,
                    0L,
                    JdbcMaps.str(body, "remark")
            );
            if (initialBinding != null) {
                Long meterId = jdbcTemplate.queryForObject(
                        "SELECT id FROM energy_meter WHERE code = ? AND deleted = 0 LIMIT 1",
                        Long.class,
                        meterCode
                );
                insertInitialMeterBinding(meterId, initialBinding);
            }
            return ApiResponse.success();
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(400, "表具编号已存在");
        }
    }

    private InitialMeterBinding initialMeterBinding(Map<String, Object> body, MeterLocation location) {
        Long tenantId = JdbcMaps.longVal(body, "tenantId");
        String subjectType = JdbcMaps.str(body, "subjectType");
        if ((subjectType == null || subjectType.isBlank()) && tenantId != null) {
            subjectType = "TENANT";
        }
        if (subjectType == null || subjectType.isBlank()) {
            return null;
        }
        subjectType = subjectType.trim().toUpperCase(Locale.ROOT);
        if (!METER_SUBJECT_TYPES.contains(subjectType)) {
            throw new BusinessException(400, "计量主体类型不正确");
        }
        if ("TENANT".equals(subjectType) && tenantId == null) {
            throw new BusinessException(400, "租户计量主体必须选择租户");
        }
        if (!"TENANT".equals(subjectType) && tenantId != null) {
            throw new BusinessException(400, "非租户计量主体不能绑定租户");
        }
        if (tenantId != null) {
            Long tenantCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tenant WHERE id = ? AND deleted = 0 AND status IN ('ACTIVE', 'ENABLED')",
                    Long.class,
                    tenantId
            );
            if (tenantCount == null || tenantCount == 0) {
                throw new BusinessException(400, "租户不存在或已停用");
            }
        }
        String effectiveFromValue = JdbcMaps.date(
                body,
                JdbcMaps.date(body, LocalDate.now().toString(), "commissioningDate"),
                "effectiveFrom"
        );
        try {
            LocalDate effectiveFrom = LocalDate.parse(effectiveFromValue);
            String commissioningDateValue = JdbcMaps.date(body, null, "commissioningDate");
            if (commissioningDateValue != null) {
                LocalDate commissioningDate = LocalDate.parse(commissioningDateValue);
                if (effectiveFrom.isBefore(commissioningDate)) {
                    throw new BusinessException(
                            400,
                            "计量归属开始日期不能早于表具投运日期 " + commissioningDate
                    );
                }
            }
            return new InitialMeterBinding(
                    subjectType,
                    tenantId,
                    location.roomId(),
                    effectiveFrom
            );
        } catch (DateTimeParseException exception) {
            throw new BusinessException(400, "计量归属开始日期格式必须为 YYYY-MM-DD");
        }
    }

    private void insertInitialMeterBinding(Long meterId, InitialMeterBinding binding) {
        jdbcTemplate.update(
                """
                INSERT INTO energy_meter_binding (
                    code, meter_id, subject_type, tenant_id, room_id,
                    effective_from, effective_to, status, created_by, updated_by, remark
                )
                VALUES (?, ?, ?, ?, ?, ?, NULL, 'ACTIVE', 0, 0, ?)
                """,
                JdbcMaps.code("EMB"),
                meterId,
                binding.subjectType(),
                binding.tenantId(),
                binding.roomId(),
                java.sql.Date.valueOf(binding.effectiveFrom()),
                "新增表具时建立初始计量主体绑定"
        );
        jdbcTemplate.update(
                "UPDATE energy_meter SET tenant_id = ?, updated_by = 0 WHERE id = ? AND deleted = 0",
                binding.tenantId(),
                meterId
        );
    }

    @PutMapping("/energy/meters/{id}")
    @Transactional
    public ApiResponse<Void> updateMeter(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String meterNo = JdbcMaps.requiredStr(body, "表具编号不能为空", "meterNo", "meterCode");
        String meterType = normalizeMeterType(JdbcMaps.strOr(body, "ELECTRIC", "meterType"));
        String status = normalizeMeterStatus(JdbcMaps.strOr(body, "ENABLED", "status"));
        MeterLocation location = meterLocation(body);
        BigDecimal initialReading = nonNegativeDecimal(body, BigDecimal.ZERO, "initialReading");
        String commissioningDate = JdbcMaps.date(body, null, "commissioningDate");
        try {
            int updated = jdbcTemplate.update(
                    """
                    UPDATE energy_meter
                    SET meter_no = ?,
                        meter_name = ?,
                        meter_type = ?,
                        building_id = ?,
                        floor_id = ?,
                        room_id = ?,
                        location_description = ?,
                        unit = ?,
                        multiplier = ?,
                        initial_reading = ?,
                        commissioning_date = ?,
                        status = ?,
                        updated_by = ?,
                        remark = ?
                    WHERE id = ? AND deleted = 0
                    """,
                    meterNo,
                    JdbcMaps.strOr(body, meterNo, "meterName"),
                    meterType,
                    location.buildingId(),
                    location.floorId(),
                    location.roomId(),
                    JdbcMaps.str(body, "locationDescription"),
                    JdbcMaps.strOr(body, defaultUnit(meterType), "unit"),
                    positiveDecimal(body, BigDecimal.ONE, "multiplier"),
                    initialReading,
                    commissioningDate,
                    status,
                    0L,
                    JdbcMaps.str(body, "remark"),
                    id
            );
            ensureUpdated(updated, "表具不存在");
            if (commissioningDate != null) {
                reconcileSoleOpenBindingStart(id, LocalDate.parse(commissioningDate));
            }
            return ApiResponse.success();
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(400, "表具编号已存在");
        }
    }

    @DeleteMapping("/energy/meters/{id}")
    public ApiResponse<Void> deleteMeter(@PathVariable Long id) {
        Long readings = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM energy_reading WHERE meter_id = ? AND deleted = 0",
                Long.class,
                id
        );
        if (readings != null && readings > 0) {
            throw new BusinessException(400, "该表具已有抄表记录，不能直接删除");
        }
        ensureUpdated(jdbcTemplate.update("UPDATE energy_meter SET deleted = 1, updated_by = ? WHERE id = ? AND deleted = 0", 0L, id), "表具不存在");
        return ApiResponse.success();
    }

    @GetMapping("/energy/meters/{id}/last-reading")
    public ApiResponse<Map<String, Object>> lastReading(
            @PathVariable Long id,
            @RequestParam(required = false) String periodMonth) {
        String normalizedPeriod = optionalPeriodMonth(periodMonth);
        String previousReadingSql = normalizedPeriod == null
                ? """
                         SELECT er.current_reading
                         FROM energy_reading er
                         WHERE er.meter_id = m.id AND er.deleted = 0
                         ORDER BY er.reading_date DESC, er.id DESC
                         LIMIT 1
                  """
                : """
                         SELECT er.current_reading
                         FROM energy_reading er
                         WHERE er.meter_id = m.id
                           AND er.period_month < ?
                           AND er.deleted = 0
                         ORDER BY er.period_month DESC, er.reading_date DESC, er.id DESC
                         LIMIT 1
                  """;
        List<Object> args = new ArrayList<>();
        if (normalizedPeriod != null) {
            args.add(normalizedPeriod);
        }
        args.add(id);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT m.id,
                       m.meter_no AS meterNo,
                       m.meter_type AS meterType,
                       m.unit,
                       m.multiplier,
                       r.room_no AS roomName,
                       t.name AS tenantName,
                       COALESCE((
                """ + previousReadingSql + """
                       ), m.initial_reading, 0) AS previousReading
                FROM energy_meter m
                LEFT JOIN building_room r ON r.id = m.room_id
                LEFT JOIN tenant t ON t.id = m.tenant_id
                WHERE m.id = ? AND m.deleted = 0
                """,
                args.toArray()
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "表具不存在");
        }
        return ApiResponse.success(rows.getFirst());
    }

    @GetMapping("/energy/price-settings")
    public ApiResponse<Map<String, Object>> priceSettings(@RequestParam(required = false) String periodMonth) {
        String month = periodMonth == null || periodMonth.isBlank() ? currentMonth() : periodMonth.trim();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT meter_type AS meterType,
                       unit_price AS unitPrice,
                       remark
                FROM energy_price_setting
                WHERE period_month = ? AND deleted = 0
                ORDER BY FIELD(meter_type, 'ELECTRIC', 'WATER', 'GAS'), meter_type
                """,
                month
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("periodMonth", month);
        for (String type : PRICE_SETTING_TYPES) {
            result.put(priceFieldName(type), BigDecimal.ZERO);
        }
        for (Map<String, Object> row : rows) {
            String type = normalizeMeterType(String.valueOf(row.get("meterType")));
            result.put(priceFieldName(type), decimalValue(row.get("unitPrice")).setScale(4, RoundingMode.HALF_UP));
        }
        result.put("prices", rows);
        return ApiResponse.success(result);
    }

    @PutMapping("/energy/price-settings")
    @Transactional
    public ApiResponse<Map<String, Object>> savePriceSettings(@RequestBody Map<String, Object> body) {
        String periodMonth = requiredPeriodMonth(body);
        int saved = 0;
        int updatedReadings = 0;
        for (String type : PRICE_SETTING_TYPES) {
            BigDecimal price = priceSettingValue(body, type);
            if (price == null) {
                continue;
            }
            jdbcTemplate.update(
                    """
                    INSERT INTO energy_price_setting (code, period_month, meter_type, unit_price, created_by, updated_by)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                      unit_price = VALUES(unit_price),
                      deleted = 0,
                      status = 'ENABLED',
                      updated_by = VALUES(updated_by)
                    """,
                    JdbcMaps.code("EPR"),
                    periodMonth,
                    type,
                    price,
                    0L,
                    0L
            );
            updatedReadings += jdbcTemplate.update(
                    """
                    UPDATE energy_reading er
                    JOIN energy_meter m ON m.id = er.meter_id AND m.deleted = 0
                    SET er.unit_price = ?,
                        er.settlement_amount = ROUND(er.usage_amount * ?, 2),
                        er.updated_by = ?
                    WHERE er.period_month = ?
                      AND m.meter_type = ?
                      AND er.deleted = 0
                      AND er.bill_status = 'NOT_GENERATED'
                    """,
                    price,
                    price,
                    0L,
                    periodMonth,
                    type
            );
            saved++;
        }
        if (saved == 0) {
            throw new BusinessException(400, "请至少填写一种能耗单价");
        }
        return ApiResponse.success(Map.of(
                "periodMonth", periodMonth,
                "savedCount", saved,
                "updatedReadingCount", updatedReadings
        ));
    }

    @GetMapping("/energy/readings")
    public ApiResponse<PageResponse<Map<String, Object>>> readings(
            @RequestParam(required = false) String meterType,
            @RequestParam(required = false) String periodMonth,
            @RequestParam(required = false) String meterNo,
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long floorId,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String tenantScope,
            @RequestParam(required = false) String billStatus,
            @RequestParam(required = false) Boolean abnormalFlag,
            @RequestParam(required = false) String customFilters,
            @RequestParam(required = false) String sorts,
            @RequestParam(required = false) String groupBy,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        QueryParts query = readingQuery(meterType, periodMonth, meterNo, buildingId, floorId, roomId, tenantId, tenantScope, billStatus, abnormalFlag);
        CompiledQuery customQuery = compileCustomQuery(
                "energy-records", customFilters, groupBy, sorts,
                READING_ORDERABLE_FIELDS, "er.reading_date", "er.id"
        );
        String where = query.where();
        List<Object> args = new ArrayList<>(query.args());
        if (customQuery != null && customQuery.hasPredicates()) {
            where += " AND " + customQuery.predicateSql();
            args.addAll(customQuery.predicateArguments());
        }
        String orderBy = customQuery == null || !customQuery.hasOrderings()
                ? WorkspaceOrderBy.build(
                        READING_ORDERABLE_FIELDS, groupBy, sorts, null, null,
                        "er.reading_date", "er.id"
                )
                : String.join(", ", customQuery.orderExpressions());
        PageResponse<Map<String, Object>> result = JdbcPagination.query(
                jdbcTemplate,
                READING_SELECT_SQL + where + " ORDER BY " + orderBy,
                READING_COUNT_SQL + where,
                args,
                customQuery == null ? List.of() : customQuery.orderArguments(),
                page,
                pageSize
        );
        if (customFieldProjectionService != null) {
            result = customFieldProjectionService.attach("energy-records", result);
        }
        return ApiResponse.success(result);
    }

    public ApiResponse<PageResponse<Map<String, Object>>> readings(
            String meterType,
            String periodMonth,
            String meterNo,
            Long buildingId,
            Long floorId,
            Long roomId,
            Long tenantId,
            String billStatus,
            Boolean abnormalFlag,
            String customFilters,
            String sorts,
            String groupBy,
            Integer page,
            Integer pageSize) {
        return readings(
                meterType, periodMonth, meterNo, buildingId, floorId, roomId, tenantId,
                null, billStatus, abnormalFlag, customFilters, sorts, groupBy, page, pageSize
        );
    }

    public ApiResponse<PageResponse<Map<String, Object>>> readings(
            String meterType,
            String periodMonth,
            String meterNo,
            Long buildingId,
            Long floorId,
            Long roomId,
            Long tenantId,
            String billStatus,
            Boolean abnormalFlag,
            String sorts,
            String groupBy,
            Integer page,
            Integer pageSize) {
        return readings(
                meterType, periodMonth, meterNo, buildingId, floorId, roomId, tenantId,
                null, billStatus, abnormalFlag, null, sorts, groupBy, page, pageSize
        );
    }

    public ApiResponse<PageResponse<Map<String, Object>>> readings(
            String meterType,
            String periodMonth,
            String meterNo,
            Long buildingId,
            Long floorId,
            Long roomId,
            Long tenantId,
            String billStatus,
            Boolean abnormalFlag,
            Integer page,
            Integer pageSize) {
        return readings(
                meterType,
                periodMonth,
                meterNo,
                buildingId,
                floorId,
                roomId,
                tenantId,
                null,
                billStatus,
                abnormalFlag,
                null,
                null,
                null,
                page,
                pageSize
        );
    }

    private CompiledQuery compileCustomQuery(
            String workspaceKey,
            String customFilters,
            String groupBy,
            String sorts,
            Map<String, String> allowedFields,
            String defaultExpression,
            String stableExpression
    ) {
        if (customFieldQueryService == null) return null;
        return customFieldQueryService.compile(
                workspaceKey,
                customFilters,
                groupBy,
                sorts,
                NativeOrderPolicy.of(
                        allowedFields,
                        null,
                        null,
                        defaultExpression,
                        stableExpression
                )
        );
    }

    @GetMapping("/energy/readings/template")
    public ResponseEntity<byte[]> downloadReadingTemplate(
            @RequestParam(required = false) String meterType,
            @RequestParam(required = false) String periodMonth,
            @RequestParam(required = false) String meterNo,
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long floorId,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String tenantScope,
            @RequestParam(required = false) String billStatus,
            @RequestParam(required = false) Boolean abnormalFlag) {
        QueryParts query = readingQuery(meterType, periodMonth, meterNo, buildingId, floorId, roomId, tenantId, tenantScope, billStatus, abnormalFlag);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                READING_SELECT_SQL + query.where() + " ORDER BY er.reading_date DESC, er.id DESC LIMIT 5000",
                query.args().toArray()
        );
        List<ImportFieldHeader> customHeaders = requireCustomFieldImportService().enabledHeaders("energy-records");
        if (customFieldProjectionService != null) {
            rows = customFieldProjectionService.attach("energy-records", rows);
        }
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("表号,表具名称,能耗类型,账期,抄表日期,上期读数,本次读数,单价,房间,租户,备注");
        customHeaders.forEach(header -> csv.append(',').append(csvValue(header.fieldName())));
        csv.append('\n');
        for (Map<String, Object> row : rows) {
            csv.append(csvValue(row.get("meterNo"))).append(',')
                    .append(csvValue(row.get("meterName"))).append(',')
                    .append(csvValue(row.get("meterType"))).append(',')
                    .append(csvValue(row.get("periodMonth"))).append(',')
                    .append(csvValue(row.get("readingDate"))).append(',')
                    .append(csvValue(row.get("previousReading"))).append(',')
                    .append(csvValue(row.get("currentReading"))).append(',')
                    .append(csvValue(row.get("unitPrice"))).append(',')
                    .append(csvValue(row.get("roomName"))).append(',')
                    .append(csvValue(row.get("tenantName"))).append(',')
                    .append(csvValue(row.get("remark")));
            Object customFields = row.get("customFields");
            Map<?, ?> customValues = customFields instanceof Map<?, ?> values ? values : Map.of();
            customHeaders.forEach(header -> csv.append(',').append(csvValue(
                    customFieldExportValue(header, customValues.get(header.fieldCode()))
            )));
            csv.append('\n');
        }
        String month = periodMonth == null || periodMonth.isBlank() ? currentMonth() : periodMonth.trim();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"energy-reading-template-" + month + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    public ResponseEntity<byte[]> downloadReadingTemplate(
            String meterType,
            String periodMonth,
            String meterNo,
            Long buildingId,
            Long floorId,
            Long roomId,
            Long tenantId,
            String billStatus,
            Boolean abnormalFlag) {
        return downloadReadingTemplate(
                meterType, periodMonth, meterNo, buildingId, floorId, roomId, tenantId,
                null, billStatus, abnormalFlag
        );
    }

    @PostMapping("/energy/readings/monthly-tasks")
    public ApiResponse<Map<String, Object>> generateMonthlyReadingTasks(
            @RequestBody Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        String operatorName = authenticatedOperatorName(principal);
        Long operatorId = authenticatedOperatorId(principal);
        String periodMonth = requiredPeriodMonth(body);
        String readingDate = JdbcMaps.date(body, LocalDate.now().toString(), "readingDate");
        BigDecimal unitPrice = JdbcMaps.decimal(body, BigDecimal.ONE, "unitPrice");
        String meterType = optionalMeterType(JdbcMaps.str(body, "meterType"));
        Long buildingId = JdbcMaps.longVal(body, "buildingId");
        Long floorId = JdbcMaps.longVal(body, "floorId");
        Long roomId = JdbcMaps.longVal(body, "roomId");
        Long tenantId = JdbcMaps.longVal(body, "tenantId");
        String tenantScope = JdbcMaps.str(body, "tenantScope");

        QueryParts query = meterQuery(null, meterType, "ENABLED", buildingId, floorId, roomId, tenantId, tenantScope);
        List<Map<String, Object>> meterRows = jdbcTemplate.queryForList(
                METER_SELECT_SQL + query.where() + " ORDER BY m.meter_no ASC, m.id ASC",
                query.args().toArray()
        );

        int created = 0;
        int skippedExisting = 0;
        for (Map<String, Object> meter : meterRows) {
            Long meterId = ((Number) meter.get("id")).longValue();
            Long existing = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM energy_reading WHERE meter_id = ? AND period_month = ? AND deleted = 0",
                    Long.class,
                    meterId,
                    periodMonth
            );
            if (existing != null && existing > 0) {
                skippedExisting++;
                continue;
            }

            BigDecimal previousReading = previousReadingValue(meterId, periodMonth);
            BigDecimal multiplier = decimalValue(meter.get("multiplier"));
            Object rowMeterType = meter.get("meterType");
            String appliedMeterType = rowMeterType == null ? meterType : String.valueOf(rowMeterType);
            BigDecimal appliedUnitPrice = configuredUnitPrice(periodMonth, appliedMeterType, unitPrice);
            insertReading(
                    meterId,
                    periodMonth,
                    readingDate,
                    previousReading,
                    previousReading,
                    BigDecimal.ZERO,
                    multiplier,
                    appliedUnitPrice,
                    BigDecimal.ZERO,
                    operatorName,
                    operatorId,
                    0,
                    null,
                    "AUTO",
                    null,
                    null,
                    JdbcMaps.strOr(body, "系统按账期批量生成待抄表任务", "remark")
            );
            created++;
        }

        return ApiResponse.success(Map.of(
                "meterCount", meterRows.size(),
                "createdCount", created,
                "skippedExistingCount", skippedExisting
        ));
    }

    @PostMapping("/energy/readings/import")
    public ApiResponse<Map<String, Object>> importReadingValues(
            @RequestBody Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        authenticatedOperatorName(principal);
        String periodMonth = requiredPeriodMonth(body);
        String readingDate = JdbcMaps.date(body, LocalDate.now().toString(), "readingDate");
        BigDecimal defaultUnitPrice = JdbcMaps.decimal(body, BigDecimal.ONE, "unitPrice");
        Object rawReadings = body.get("readings");
        if (!(rawReadings instanceof List<?> readings) || readings.isEmpty()) {
            throw new BusinessException(400, "导入读数不能为空");
        }

        int success = 0;
        int created = 0;
        int updated = 0;
        List<Map<String, Object>> failures = new ArrayList<>();
        for (Object rawItem : readings) {
            if (!(rawItem instanceof Map<?, ?> rawMap)) {
                failures.add(Map.of("meterNo", "-", "message", "导入行格式不正确"));
                continue;
            }
            Map<String, Object> item = normalizeBody(rawMap);
            String meterNo = JdbcMaps.str(item, "meterNo", "表号", "表具编号");
            try {
                if (meterNo == null) {
                    throw new BusinessException(400, "表号不能为空");
                }
                Map<String, Object> meter = findMeterByNo(meterNo);
                Long meterId = ((Number) meter.get("id")).longValue();
                Long readingId = existingReadingId(meterId, periodMonth);
                Map<String, Object> payload = new java.util.HashMap<>();
                payload.put("meterId", meterId);
                payload.put("periodMonth", periodMonth);
                payload.put("readingDate", JdbcMaps.strOr(item, readingDate, "readingDate", "抄表日期"));
                payload.put("currentReading", JdbcMaps.requiredStr(item, "本次读数不能为空", "currentReading", "本次读数"));
                String rowUnitPrice = JdbcMaps.str(item, "unitPrice", "单价");
                if (rowUnitPrice == null) {
                    BigDecimal configuredPrice = configuredUnitPrice(periodMonth, String.valueOf(meter.get("meterType")), defaultUnitPrice);
                    payload.put("unitPrice", configuredPrice.toPlainString());
                } else {
                    payload.put("unitPrice", rowUnitPrice);
                }
                payload.put("remark", JdbcMaps.strOr(item, "导入读数", "remark", "备注"));
                payload.put("source", "IMPORT");
                if (readingId != null) {
                    payload.put("changeReason", JdbcMaps.strOr(
                            item,
                            "批量导入更新既有抄表记录",
                            "changeReason",
                            "变更原因"
                    ));
                }
                saveReading(readingId, payload, principal);
                Long savedReadingId = readingId == null ? existingReadingId(meterId, periodMonth) : readingId;
                if (savedReadingId == null) {
                    throw new BusinessException(500, "抄表记录保存后读取失败");
                }
                requireCustomFieldImportService().saveImportedRow(
                        "energy-records",
                        savedReadingId,
                        item,
                        Map.of()
                );
                success++;
                if (readingId == null) {
                    created++;
                } else {
                    updated++;
                }
            } catch (RuntimeException exception) {
                failures.add(Map.of(
                        "meterNo", meterNo == null ? "-" : meterNo,
                        "message", exception.getMessage() == null ? "导入失败" : exception.getMessage()
                ));
            }
        }

        return ApiResponse.success(Map.of(
                "successCount", success,
                "createdCount", created,
                "updatedCount", updated,
                "failureCount", failures.size(),
                "failures", failures
        ));
    }

    private CustomFieldImportService requireCustomFieldImportService() {
        if (customFieldImportService == null) {
            throw new IllegalStateException("Custom-field import service is unavailable");
        }
        return customFieldImportService;
    }

    private Object customFieldExportValue(ImportFieldHeader header, Object value) {
        if (value == null) return "";
        return switch (header.fieldType()) {
            case SINGLE_SELECT -> header.optionLabels().getOrDefault(String.valueOf(value), String.valueOf(value));
            case MULTI_SELECT -> value instanceof List<?> values
                    ? values.stream()
                    .map(item -> header.optionLabels().getOrDefault(String.valueOf(item), String.valueOf(item)))
                    .collect(java.util.stream.Collectors.joining("、"))
                    : String.valueOf(value);
            case BOOLEAN -> Boolean.TRUE.equals(value) ? "是" : "否";
            default -> value;
        };
    }

    @PostMapping({
            "/energy/readings/batch-confirm-charge-bases",
            "/energy/readings/batch-generate-bills"
    })
    public ApiResponse<Map<String, Object>> batchGenerateBills(
            @RequestBody Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        List<Long> ids = readingIdsForBatchBill(body);
        int success = 0;
        List<Map<String, Object>> failures = new ArrayList<>();
        for (Long id : ids) {
            try {
                generateBill(id, principal);
                success++;
            } catch (RuntimeException exception) {
                failures.add(Map.of(
                        "id", id,
                        "message", exception.getMessage() == null ? "生成账单失败" : exception.getMessage()
                ));
            }
        }
        return ApiResponse.success(Map.of(
                "candidateCount", ids.size(),
                "successCount", success,
                "failureCount", failures.size(),
                "failures", failures
        ));
    }

    @PostMapping("/energy/readings")
    @Transactional
    public ApiResponse<Void> createReading(
            @RequestBody Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        saveReading(null, body, principal);
        return ApiResponse.success();
    }

    @PutMapping("/energy/readings/{id}")
    @Transactional
    public ApiResponse<Void> updateReading(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        saveReading(id, body, principal);
        return ApiResponse.success();
    }

    @DeleteMapping("/energy/readings/{id}")
    @Transactional
    public ApiResponse<Void> deleteReading(
            @PathVariable Long id,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        Map<String, Object> before = readingSnapshot(id);
        ensureUpdated(jdbcTemplate.update(
                "UPDATE energy_reading SET deleted = 1, updated_by = ? WHERE id = ? AND deleted = 0 AND bill_status = 'NOT_GENERATED'",
                authenticatedOperatorId(principal),
                id
        ), "抄表记录不存在或已生成账单");
        auditReading(id, "DELETE", before, null, "Deleted from energy management", authenticatedOperatorId(principal));
        return ApiResponse.success();
    }

    @PatchMapping("/energy/readings/{id}/anomaly-status")
    public ApiResponse<Void> updateAnomalyStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        String status = normalizeAnomalyStatus(JdbcMaps.requiredStr(body, "异常状态不能为空", "abnormalStatus", "status"));
        ensureUpdated(jdbcTemplate.update(
                "UPDATE energy_reading SET abnormal_status = ?, updated_by = ? WHERE id = ? AND deleted = 0",
                status,
                authenticatedOperatorId(principal),
                id
        ), "抄表记录不存在");
        return ApiResponse.success();
    }

    @PostMapping({
            "/energy/readings/{id}/confirm-charge-basis",
            "/energy/readings/{id}/generate-bill"
    })
    @Transactional
    public ApiResponse<Void> generateBill(
            @PathVariable Long id,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        assetChargeBasisService.confirmEnergyBasis(id, authenticatedOperatorId(principal));
        return ApiResponse.success();
    }

    @Transactional
    @PostMapping("/energy/readings/{id}/withdraw-bill")
    public ApiResponse<Void> withdrawBill(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        String operatorName = authenticatedOperatorName(principal);
        Long operatorId = authenticatedOperatorId(principal);
        Map<String, Object> reading = findReadingForBill(id);
        Long billId = longValue(reading.get("bill_id"));
        String billStatus = String.valueOf(reading.get("bill_status"));
        if (billId == null && "BASIS_CONFIRMED".equals(billStatus)) {
            assetChargeBasisService.cancelUnbilledBasis(
                    "ENERGY",
                    id,
                    operatorId,
                    JdbcMaps.str(body, "reason")
            );
            ensureUpdated(jdbcTemplate.update(
                    """
                    UPDATE energy_reading
                    SET bill_status = 'NOT_GENERATED',
                        charge_basis_id = NULL,
                        updated_by = ?
                    WHERE id = ? AND deleted = 0
                    """,
                    operatorId,
                    id
            ), "抄表记录不存在");
            return ApiResponse.success();
        }
        if (billId == null && "NOT_GENERATED".equals(billStatus)) {
            return ApiResponse.success();
        }
        if (billId == null) {
            throw new BusinessException(400, "未找到关联财务账单，请检查数据");
        }

        Map<String, Object> bill = findLinkedEnergyBill(billId, id);
        String payStatus = String.valueOf(bill.get("payStatus"));
        BigDecimal paidAmount = decimalValue(bill.get("paidAmount"));
        if ("PAID".equals(payStatus) || paidAmount.compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(400, "该账单已收款，不能撤回");
        }

        List<Map<String, Object>> invoices = activeInvoices(billId);
        if (!invoices.isEmpty()) {
            Map<String, Object> invoice = invoices.getFirst();
            if (booleanValue(invoice.get("hasInvoiceFile"))) {
                throw new BusinessException(400, "该账单已上传发票 PDF，不能撤回");
            }
            throw new BusinessException(400, "该账单已开票，不能撤回");
        }

        if (!"CANCELLED".equals(payStatus)) {
            ensureUpdated(jdbcTemplate.update(
                    """
                    UPDATE bill
                    SET pay_status = 'CANCELLED',
                        status = 'CANCELLED',
                        audit_status = 'REJECTED',
                        updated_by = ?
                    WHERE id = ? AND deleted = 0
                    """,
                    operatorId,
                    billId
            ), "未找到关联财务账单，请检查数据");
        }
        ensureUpdated(jdbcTemplate.update(
                """
                UPDATE energy_reading
                SET bill_status = 'NOT_GENERATED',
                    bill_id = NULL,
                    updated_by = ?
                WHERE id = ? AND deleted = 0
                """,
                operatorId,
                id
        ), "抄表记录不存在");
        logEnergyBillOperation(
                id,
                billId,
                "WITHDRAW_BILL",
                JdbcMaps.str(body, "reason"),
                "SUCCESS",
                "账单已撤回",
                operatorId,
                operatorName
        );
        return ApiResponse.success();
    }

    @PostMapping("/energy/readings/{id}/mark-posted")
    public ApiResponse<Void> markPosted(
            @PathVariable Long id,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        ensureUpdated(jdbcTemplate.update(
                "UPDATE energy_reading SET bill_status = 'POSTED', updated_by = ? WHERE id = ? AND deleted = 0 AND bill_status = 'GENERATED'",
                authenticatedOperatorId(principal),
                id
        ), "抄表记录不存在或未生成账单");
        return ApiResponse.success();
    }

    @GetMapping("/energy/stats")
    public ApiResponse<Map<String, Object>> stats(
            @RequestParam(required = false) String meterType,
            @RequestParam(required = false) String periodMonth,
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long floorId,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String tenantScope,
            @RequestParam(required = false) String billStatus,
            @RequestParam(required = false) Boolean abnormalFlag) {
        QueryParts query = readingQuery(meterType, periodMonth, null, buildingId, floorId, roomId, tenantId, tenantScope, billStatus, abnormalFlag);
        List<Map<String, Object>> typeRows = jdbcTemplate.queryForList(
                """
                SELECT m.meter_type AS meterType,
                       COUNT(*) AS recordCount,
                       COALESCE(SUM(er.usage_amount), 0) AS usageAmount,
                       COALESCE(SUM(er.settlement_amount), 0) AS settlementAmount,
                       COALESCE(SUM(CASE WHEN er.abnormal_flag = 1 THEN 1 ELSE 0 END), 0) AS abnormalCount
                FROM energy_reading er
                JOIN energy_meter m ON m.id = er.meter_id AND m.deleted = 0
                LEFT JOIN building b ON b.id = m.building_id
                LEFT JOIN building_floor f ON f.id = m.floor_id
                LEFT JOIN building_room r ON r.id = m.room_id
                LEFT JOIN tenant t ON t.id = m.tenant_id
                WHERE er.deleted = 0
                """ + query.where() + "\n" + """
                GROUP BY m.meter_type
                """,
                query.args().toArray()
        );
        Map<String, Map<String, Object>> byType = typeRows.stream()
                .collect(java.util.stream.Collectors.toMap(row -> String.valueOf(row.get("meterType")), row -> row));
        BigDecimal electric = aggregateDecimal(byType, "ELECTRIC", "usageAmount");
        BigDecimal water = aggregateDecimal(byType, "WATER", "usageAmount");
        BigDecimal gas = aggregateDecimal(byType, "GAS", "usageAmount");
        BigDecimal electricAmount = aggregateDecimal(byType, "ELECTRIC", "settlementAmount");
        BigDecimal waterAmount = aggregateDecimal(byType, "WATER", "settlementAmount");
        BigDecimal gasAmount = aggregateDecimal(byType, "GAS", "settlementAmount");
        BigDecimal amount = electricAmount.add(waterAmount).add(gasAmount);
        long recordCount = typeRows.stream().mapToLong(row -> ((Number) row.get("recordCount")).longValue()).sum();
        long abnormalCount = typeRows.stream().mapToLong(row -> ((Number) row.get("abnormalCount")).longValue()).sum();
        List<Map<String, Object>> anomalies = jdbcTemplate.queryForList(
                READING_SELECT_SQL + query.where() + " AND er.abnormal_flag = 1 ORDER BY er.reading_date DESC, er.id DESC LIMIT 20",
                query.args().toArray()
        );
        List<Map<String, Object>> trend = groupedEnergyStats(query, """
                er.period_month AS periodMonth,
                COUNT(*) AS recordCount,
                SUM(CASE WHEN m.meter_type = 'ELECTRIC' THEN 1 ELSE 0 END) AS electricRecordCount,
                SUM(CASE WHEN m.meter_type = 'WATER' THEN 1 ELSE 0 END) AS waterRecordCount,
                SUM(CASE WHEN m.meter_type = 'GAS' THEN 1 ELSE 0 END) AS gasRecordCount,
                SUM(CASE WHEN m.meter_type = 'ELECTRIC' THEN er.usage_amount ELSE 0 END) AS electricUsage,
                SUM(CASE WHEN m.meter_type = 'WATER' THEN er.usage_amount ELSE 0 END) AS waterUsage,
                SUM(CASE WHEN m.meter_type = 'GAS' THEN er.usage_amount ELSE 0 END) AS gasUsage,
                SUM(er.settlement_amount) AS settlementAmount
                """, "er.period_month", "er.period_month ASC");
        List<Map<String, Object>> roomRanking = groupedEnergyStats(query, """
                m.room_id AS roomId, COALESCE(r.room_no, '未关联房间') AS roomName,
                m.meter_type AS meterType, COUNT(*) AS recordCount,
                SUM(er.usage_amount) AS usageAmount, SUM(er.settlement_amount) AS settlementAmount
                """, "m.room_id, r.room_no, m.meter_type", "usageAmount DESC, m.room_id ASC");
        List<Map<String, Object>> buildingRanking = groupedEnergyStats(query, """
                m.building_id AS buildingId, COALESCE(b.name, '未关联楼宇') AS buildingName,
                m.meter_type AS meterType, COUNT(*) AS recordCount,
                SUM(er.usage_amount) AS usageAmount, SUM(er.settlement_amount) AS settlementAmount
                """, "m.building_id, b.name, m.meter_type", "usageAmount DESC, m.building_id ASC");
        return ApiResponse.success(Map.ofEntries(
                Map.entry("recordCount", recordCount),
                Map.entry("electricUsage", electric),
                Map.entry("waterUsage", water),
                Map.entry("gasUsage", gas),
                Map.entry("totalAmount", amount),
                Map.entry("averageUsage", recordCount == 0 ? BigDecimal.ZERO : electric.add(water).add(gas).divide(BigDecimal.valueOf(recordCount), 2, RoundingMode.HALF_UP)),
                Map.entry("abnormalCount", abnormalCount),
                Map.entry("trend", trend),
                Map.entry("typeStructure", List.of(
                        Map.of("meterType", "ELECTRIC", "usageAmount", electric, "settlementAmount", electricAmount, "recordCount", aggregateDecimal(byType, "ELECTRIC", "recordCount")),
                        Map.of("meterType", "WATER", "usageAmount", water, "settlementAmount", waterAmount, "recordCount", aggregateDecimal(byType, "WATER", "recordCount")),
                        Map.of("meterType", "GAS", "usageAmount", gas, "settlementAmount", gasAmount, "recordCount", aggregateDecimal(byType, "GAS", "recordCount"))
                )),
                Map.entry("roomRanking", roomRanking),
                Map.entry("buildingRanking", buildingRanking),
                Map.entry("anomalies", anomalies)
        ));
    }

    private List<Map<String, Object>> groupedEnergyStats(QueryParts query, String columns, String groupBy, String orderBy) {
        // Grouping fragments are server-owned constants; every user filter stays bound as a parameter.
        String from = """
                FROM energy_reading er
                JOIN energy_meter m ON m.id = er.meter_id AND m.deleted = 0
                LEFT JOIN building b ON b.id = m.building_id
                LEFT JOIN building_floor f ON f.id = m.floor_id
                LEFT JOIN building_room r ON r.id = m.room_id
                LEFT JOIN tenant t ON t.id = m.tenant_id
                WHERE er.deleted = 0
                """;
        return jdbcTemplate.queryForList(
                "SELECT " + columns + "\n" + from + query.where()
                        + "\nGROUP BY " + groupBy + "\nORDER BY " + orderBy,
                query.args().toArray()
        );
    }

    private String csvValue(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private String currentMonth() {
        return LocalDate.now().toString().substring(0, 7);
    }

    private void saveReading(Long id, Map<String, Object> body, AuthPrincipal principal) {
        String operatorName = authenticatedOperatorName(principal);
        Long operatorId = authenticatedOperatorId(principal);
        Long meterId = JdbcMaps.requiredLong(body, "请选择表具", "meterId");
        String periodMonth = requiredPeriodMonth(body);
        String readingDate = JdbcMaps.date(body, JdbcMaps.today(), "readingDate");
        BigDecimal currentReading = JdbcMaps.decimal(body, BigDecimal.ZERO, "currentReading");
        BigDecimal unitPrice = JdbcMaps.decimal(body, BigDecimal.ZERO, "unitPrice");
        Map<String, Object> meter = findMeter(meterId);
        LocalDate parsedReadingDate = LocalDate.parse(readingDate);
        LocalDate commissioningDate = meterCommissioningDate(meter);
        if (commissioningDate != null && parsedReadingDate.isBefore(commissioningDate)) {
            throw new BusinessException(
                    400,
                    "抄表日期不能早于表具投运日期 " + commissioningDate
            );
        }
        BigDecimal multiplier = decimalValue(meter.get("multiplier"));
        BigDecimal previousReading = previousReadingValue(meterId, periodMonth);
        LocalDate desiredBindingStart = YearMonth.parse(periodMonth).atDay(1);
        if (commissioningDate != null && commissioningDate.isAfter(desiredBindingStart)) {
            desiredBindingStart = commissioningDate;
        }
        reconcileSoleOpenBindingStart(meterId, desiredBindingStart);
        String resetReason = JdbcMaps.str(body, "resetReason");
        boolean meterReset = currentReading.compareTo(previousReading) < 0;
        if (meterReset && resetReason == null) {
            throw new BusinessException(400, "Current reading is lower than the previous reading; a meter reset reason is required");
        }
        BigDecimal usage = (meterReset ? currentReading : currentReading.subtract(previousReading))
                .multiply(multiplier)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal settlement = usage.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
        String abnormalReason = meterReset ? "METER_RESET" : null;
        int abnormalFlag = abnormalReason == null ? 0 : 1;
        String source = JdbcMaps.strOr(body, "MANUAL", "source").trim().toUpperCase(Locale.ROOT);
        if (!Set.of("MANUAL", "IMPORT", "AUTO").contains(source)) {
            throw new BusinessException(400, "Reading source is invalid");
        }
        Long attachmentFileId = JdbcMaps.longVal(body, "attachmentFileId");

        try {
            if (id == null) {
                insertReading(
                        meterId,
                        periodMonth,
                        readingDate,
                        previousReading,
                        currentReading,
                        usage,
                        multiplier,
                        unitPrice,
                        settlement,
                        operatorName,
                        operatorId,
                        abnormalFlag,
                        abnormalReason,
                        source,
                        attachmentFileId,
                        resetReason,
                        JdbcMaps.str(body, "remark")
                );
                Long createdId = existingReadingId(meterId, periodMonth);
                if (createdId != null) {
                    auditReading(createdId, "CREATE", null, readingSnapshot(createdId), JdbcMaps.str(body, "changeReason"), operatorId);
                }
                recalculateLaterReadings(meterId, periodMonth, operatorId);
                return;
            }
            String changeReason = JdbcMaps.requiredStr(body, "A reason is required when modifying a historical reading", "changeReason");
            Map<String, Object> before = readingSnapshot(id);
            ensureUpdated(jdbcTemplate.update(
                    """
                    UPDATE energy_reading
                    SET meter_id = ?,
                        period_month = ?,
                        reading_date = ?,
                        previous_reading = ?,
                        current_reading = ?,
                        usage_amount = ?,
                        multiplier = ?,
                        unit_price = ?,
                        settlement_amount = ?,
                        operator_name = ?,
                        reading_source = ?,
                        attachment_file_id = ?,
                        reset_reason = ?,
                        abnormal_flag = ?,
                        abnormal_reason = ?,
                        updated_by = ?,
                        remark = ?
                    WHERE id = ? AND deleted = 0 AND bill_status = 'NOT_GENERATED'
                    """,
                    meterId,
                    periodMonth,
                    readingDate,
                    previousReading,
                    currentReading,
                    usage,
                    multiplier,
                    unitPrice,
                    settlement,
                    operatorName,
                    source,
                    attachmentFileId,
                    resetReason,
                    abnormalFlag,
                    abnormalReason,
                    operatorId,
                    JdbcMaps.str(body, "remark"),
                    id
            ), "抄表记录不存在或已生成账单");
            auditReading(id, "UPDATE", before, readingSnapshot(id), changeReason, operatorId);
            recalculateLaterReadings(meterId, periodMonth, operatorId);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "该表具 " + periodMonth + " 账期已存在抄表记录，请勿重复新增");
        }
    }

    private String requiredPeriodMonth(Map<String, Object> body) {
        String periodMonth = JdbcMaps.requiredStr(body, "请选择账期", "periodMonth");
        return parsePeriodMonth(periodMonth);
    }

    private String optionalPeriodMonth(String periodMonth) {
        if (periodMonth == null || periodMonth.isBlank()) {
            return null;
        }
        return parsePeriodMonth(periodMonth);
    }

    private String parsePeriodMonth(String periodMonth) {
        try {
            return YearMonth.parse(periodMonth).toString();
        } catch (DateTimeParseException exception) {
            throw new BusinessException(400, "账期格式不正确，请使用 YYYY-MM");
        }
    }

    private void reconcileSoleOpenBindingStart(Long meterId, LocalDate desiredFrom) {
        if (meterId == null || desiredFrom == null) {
            return;
        }
        List<Map<String, Object>> bindings = jdbcTemplate.queryForList(
                """
                SELECT id,
                       effective_from AS effectiveFrom,
                       effective_to AS effectiveTo,
                       status
                FROM energy_meter_binding
                WHERE meter_id = ?
                  AND deleted = 0
                ORDER BY effective_from ASC, id ASC
                """,
                meterId
        );
        if (bindings.size() != 1) {
            return;
        }
        Map<String, Object> binding = bindings.getFirst();
        if (!"ACTIVE".equals(String.valueOf(binding.get("status")))
                || binding.get("effectiveTo") != null
                || binding.get("effectiveFrom") == null) {
            return;
        }
        LocalDate currentFrom = LocalDate.parse(String.valueOf(binding.get("effectiveFrom")));
        if (!desiredFrom.isBefore(currentFrom)) {
            return;
        }
        jdbcTemplate.update(
                """
                UPDATE energy_meter_binding
                SET effective_from = ?,
                    updated_by = 0
                WHERE id = ?
                  AND deleted = 0
                  AND status = 'ACTIVE'
                  AND effective_to IS NULL
                  AND effective_from > ?
                """,
                java.sql.Date.valueOf(desiredFrom),
                ((Number) binding.get("id")).longValue(),
                java.sql.Date.valueOf(desiredFrom)
        );
    }

    private void recalculateLaterReadings(Long meterId, String periodMonth, Long operatorId) {
        if (periodMonth == null || periodMonth.isBlank()) {
            return;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id,
                       period_month AS periodMonth,
                       current_reading AS currentReading,
                       multiplier,
                       unit_price AS unitPrice,
                       reset_reason AS resetReason
                FROM energy_reading
                WHERE meter_id = ?
                  AND period_month > ?
                  AND deleted = 0
                  AND bill_status = 'NOT_GENERATED'
                ORDER BY period_month ASC, reading_date ASC, id ASC
                """,
                meterId,
                periodMonth.trim()
        );
        for (Map<String, Object> row : rows) {
            Long readingId = ((Number) row.get("id")).longValue();
            String laterPeriodMonth = String.valueOf(row.get("periodMonth"));
            BigDecimal previousReading = previousReadingValue(meterId, laterPeriodMonth);
            BigDecimal currentReading = decimalValue(row.get("currentReading"));
            BigDecimal multiplier = decimalValue(row.get("multiplier"));
            BigDecimal unitPrice = decimalValue(row.get("unitPrice"));
            boolean meterReset = currentReading.compareTo(previousReading) < 0;
            String resetReason = row.get("resetReason") == null ? null : String.valueOf(row.get("resetReason")).trim();
            if (meterReset && (resetReason == null || resetReason.isBlank())) {
                throw new BusinessException(409, "A later reading becomes lower than its recalculated previous reading; provide a reset reason first");
            }
            BigDecimal usage = (meterReset ? currentReading : currentReading.subtract(previousReading))
                    .multiply(multiplier)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal settlement = usage.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
            String abnormalReason = meterReset ? "METER_RESET" : null;
            int abnormalFlag = abnormalReason == null ? 0 : 1;
            jdbcTemplate.update(
                    """
                    UPDATE energy_reading
                    SET previous_reading = ?,
                        usage_amount = ?,
                        settlement_amount = ?,
                        abnormal_flag = ?,
                        abnormal_reason = ?,
                        abnormal_status = 'PENDING',
                        updated_by = ?
                    WHERE id = ? AND deleted = 0 AND bill_status = 'NOT_GENERATED'
                    """,
                    previousReading,
                    usage,
                    settlement,
                    abnormalFlag,
                    abnormalReason,
                    operatorId,
                    readingId
            );
        }
    }

    private void insertReading(
            Long meterId,
            String periodMonth,
            String readingDate,
            BigDecimal previousReading,
            BigDecimal currentReading,
            BigDecimal usage,
            BigDecimal multiplier,
            BigDecimal unitPrice,
            BigDecimal settlement,
            String operatorName,
            Long operatorId,
            int abnormalFlag,
            String abnormalReason,
            String source,
            Long attachmentFileId,
            String resetReason,
            String remark) {
        jdbcTemplate.update(
                """
                INSERT INTO energy_reading (
                  code, meter_id, period_month, reading_date, previous_reading, current_reading, usage_amount,
                  multiplier, unit_price, settlement_amount, operator_name, reading_source, attachment_file_id,
                  reset_reason, bill_status, abnormal_flag,
                  abnormal_reason, abnormal_status, status, created_by, updated_by, remark
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'NOT_GENERATED', ?, ?, 'PENDING', 'ACTIVE', ?, ?, ?)
                """,
                JdbcMaps.code("ENR"),
                meterId,
                periodMonth,
                readingDate,
                previousReading,
                currentReading,
                usage,
                multiplier,
                unitPrice,
                settlement,
                operatorName,
                source,
                attachmentFileId,
                resetReason,
                abnormalFlag,
                abnormalReason,
                operatorId,
                operatorId,
                remark
        );
    }

    private Map<String, Object> readingSnapshot(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, code, meter_id, period_month, reading_date,
                       previous_reading, current_reading, usage_amount, multiplier,
                       unit_price, settlement_amount, operator_name, reading_source,
                       attachment_file_id, reset_reason, bill_status, charge_basis_id,
                       abnormal_flag, abnormal_reason, abnormal_status, status, remark,
                       deleted, created_at, updated_at
                FROM energy_reading
                WHERE id = ?
                """,
                id
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "Reading record does not exist");
        }
        return rows.getFirst();
    }

    private void auditReading(
            Long readingId,
            String action,
            Map<String, Object> before,
            Map<String, Object> after,
            String reason,
            Long operatorId) {
        try {
            ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
            jdbcTemplate.update(
                    """
                    INSERT INTO energy_reading_audit (
                        reading_id, action, before_snapshot, after_snapshot, reason, operator_id
                    )
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    readingId,
                    action,
                    before == null ? null : mapper.writeValueAsString(before),
                    after == null ? null : mapper.writeValueAsString(after),
                    reason,
                    operatorId
            );
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize reading audit snapshot", exception);
        }
    }

    private String authenticatedOperatorName(AuthPrincipal principal) {
        if (principal == null || principal.username() == null || principal.username().isBlank()) {
            throw new BusinessException(401, "未登录或登录已过期");
        }
        return principal.username().trim();
    }

    private Long authenticatedOperatorId(AuthPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new BusinessException(401, "未登录或登录已过期");
        }
        return principal.userId();
    }

    private Map<String, Object> normalizeBody(Map<?, ?> rawMap) {
        Map<String, Object> body = new java.util.HashMap<>();
        rawMap.forEach((key, value) -> {
            if (key != null && value != null) {
                body.put(String.valueOf(key).trim(), value);
            }
        });
        return body;
    }

    private Map<String, Object> findMeterByNo(String meterNo) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                METER_SELECT_SQL + " AND m.meter_no = ?",
                meterNo
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "表具不存在：" + meterNo);
        }
        return rows.getFirst();
    }

    private Long existingReadingId(Long meterId, String periodMonth) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id FROM energy_reading WHERE meter_id = ? AND period_month = ? AND deleted = 0 ORDER BY id DESC LIMIT 1",
                meterId,
                periodMonth
        );
        return rows.isEmpty() ? null : ((Number) rows.getFirst().get("id")).longValue();
    }

    private List<Long> readingIdsForBatchBill(Map<String, Object> body) {
        Object rawIds = body.get("ids");
        if (rawIds instanceof List<?> list && !list.isEmpty()) {
            List<Long> ids = new ArrayList<>();
            for (Object rawId : list) {
                if (rawId instanceof Number number) {
                    ids.add(number.longValue());
                } else if (rawId != null && !String.valueOf(rawId).isBlank()) {
                    ids.add(Long.valueOf(String.valueOf(rawId)));
                }
            }
            return ids;
        }

        QueryParts query = readingQuery(
                optionalMeterType(JdbcMaps.str(body, "meterType")),
                JdbcMaps.str(body, "periodMonth"),
                JdbcMaps.str(body, "meterNo"),
                JdbcMaps.longVal(body, "buildingId"),
                JdbcMaps.longVal(body, "floorId"),
                JdbcMaps.longVal(body, "roomId"),
                JdbcMaps.longVal(body, "tenantId"),
                "NOT_GENERATED",
                false
        );
        return jdbcTemplate.queryForList(
                        """
                        SELECT er.id
                        FROM energy_reading er
                        JOIN energy_meter m ON m.id = er.meter_id AND m.deleted = 0
                        LEFT JOIN building b ON b.id = m.building_id
                        LEFT JOIN building_floor f ON f.id = m.floor_id
                        LEFT JOIN building_room r ON r.id = m.room_id
                        LEFT JOIN tenant t ON t.id = m.tenant_id
                        WHERE er.deleted = 0
                        """ + query.where() + " ORDER BY er.id ASC LIMIT 1000",
                        query.args().toArray()
                )
                .stream()
                .map(row -> ((Number) row.get("id")).longValue())
                .toList();
    }

    private String optionalMeterType(String meterType) {
        if (meterType == null || meterType.isBlank()) {
            return null;
        }
        return normalizeMeterType(meterType);
    }

    private QueryParts meterQuery(String keyword, String meterType, String status, Long buildingId, Long floorId, Long roomId, Long tenantId) {
        return meterQuery(keyword, meterType, status, buildingId, floorId, roomId, tenantId, null);
    }

    private QueryParts meterQuery(String keyword, String meterType, String status, Long buildingId, Long floorId, Long roomId, Long tenantId, String tenantScope) {
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            where.append(" AND (m.meter_no LIKE ? OR m.meter_name LIKE ? OR b.name LIKE ? OR f.name LIKE ? OR r.room_no LIKE ? OR t.name LIKE ?)");
            for (int i = 0; i < 6; i++) {
                args.add(like);
            }
        }
        if (meterType != null && !meterType.isBlank()) {
            where.append(" AND m.meter_type = ?");
            args.add(normalizeMeterType(meterType));
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND m.status = ?");
            args.add(normalizeMeterStatus(status));
        }
        addIdFilters(where, args, "m", buildingId, floorId, roomId, tenantId);
        addTenantScopeFilter(where, tenantId, tenantScope);
        return new QueryParts(where.toString(), args);
    }

    private QueryParts readingQuery(String meterType, String periodMonth, String meterNo, Long buildingId, Long floorId, Long roomId, Long tenantId, String billStatus, Boolean abnormalFlag) {
        return readingQuery(meterType, periodMonth, meterNo, buildingId, floorId, roomId, tenantId, null, billStatus, abnormalFlag);
    }

    private QueryParts readingQuery(String meterType, String periodMonth, String meterNo, Long buildingId, Long floorId, Long roomId, Long tenantId, String tenantScope, String billStatus, Boolean abnormalFlag) {
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (meterType != null && !meterType.isBlank()) {
            where.append(" AND m.meter_type = ?");
            args.add(normalizeMeterType(meterType));
        }
        if (periodMonth != null && !periodMonth.isBlank()) {
            where.append(" AND er.period_month = ?");
            args.add(periodMonth.trim());
        }
        if (meterNo != null && !meterNo.isBlank()) {
            where.append(" AND m.meter_no LIKE ?");
            args.add("%" + meterNo.trim() + "%");
        }
        if (billStatus != null && !billStatus.isBlank()) {
            String normalized = billStatus.trim().toUpperCase(Locale.ROOT);
            if (!BILL_STATUSES.contains(normalized)) {
                throw new BusinessException(400, "账单状态不正确");
            }
            where.append(" AND er.bill_status = ?");
            args.add(normalized);
        }
        if (abnormalFlag != null) {
            where.append(" AND er.abnormal_flag = ?");
            args.add(Boolean.TRUE.equals(abnormalFlag) ? 1 : 0);
        }
        addIdFilters(where, args, "m", buildingId, floorId, roomId, tenantId);
        addTenantScopeFilter(where, tenantId, tenantScope);
        return new QueryParts(where.toString(), args);
    }

    private void addTenantScopeFilter(StringBuilder where, Long tenantId, String tenantScope) {
        if (tenantScope == null || tenantScope.isBlank()) {
            return;
        }
        if (tenantId != null) {
            throw new BusinessException(400, "租户与主体范围不能同时指定");
        }
        if (!"PROPERTY".equalsIgnoreCase(tenantScope.trim())) {
            throw new BusinessException(400, "主体范围不正确");
        }
        where.append(" AND m.tenant_id IS NULL");
    }

    private void addIdFilters(StringBuilder where, List<Object> args, String alias, Long buildingId, Long floorId, Long roomId, Long tenantId) {
        if (buildingId != null) {
            where.append(" AND ").append(alias).append(".building_id = ?");
            args.add(buildingId);
        }
        if (floorId != null) {
            where.append(" AND ").append(alias).append(".floor_id = ?");
            args.add(floorId);
        }
        if (roomId != null) {
            where.append(" AND ").append(alias).append(".room_id = ?");
            args.add(roomId);
        }
        if (tenantId != null) {
            where.append(" AND ").append(alias).append(".tenant_id = ?");
            args.add(tenantId);
        }
    }

    private Map<String, Object> findMeter(Long meterId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM energy_meter WHERE id = ? AND deleted = 0", meterId);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "表具不存在");
        }
        return rows.getFirst();
    }

    private LocalDate meterCommissioningDate(Map<String, Object> meter) {
        Object value = meter.get("commissioning_date");
        if (value == null) {
            value = meter.get("commissioningDate");
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof LocalDate date) {
            return date;
        }
        return value == null ? null : LocalDate.parse(String.valueOf(value));
    }

    private BigDecimal lastReadingValue(Long meterId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT current_reading FROM energy_reading WHERE meter_id = ? AND deleted = 0 ORDER BY reading_date DESC, id DESC LIMIT 1",
                meterId
        );
        if (!rows.isEmpty()) {
            return decimalValue(rows.getFirst().get("current_reading"));
        }
        BigDecimal initialReading = jdbcTemplate.queryForObject(
                "SELECT initial_reading FROM energy_meter WHERE id = ? AND deleted = 0",
                BigDecimal.class,
                meterId
        );
        return initialReading == null ? BigDecimal.ZERO : initialReading;
    }

    private BigDecimal previousReadingValue(Long meterId, String periodMonth) {
        if (periodMonth == null || periodMonth.isBlank()) {
            return lastReadingValue(meterId);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT current_reading
                FROM energy_reading
                WHERE meter_id = ? AND period_month < ? AND deleted = 0
                ORDER BY period_month DESC, reading_date DESC, id DESC
                LIMIT 1
                """,
                meterId,
                periodMonth.trim()
        );
        if (!rows.isEmpty()) {
            return decimalValue(rows.getFirst().get("current_reading"));
        }
        BigDecimal initialReading = jdbcTemplate.queryForObject(
                "SELECT initial_reading FROM energy_meter WHERE id = ? AND deleted = 0",
                BigDecimal.class,
                meterId
        );
        return initialReading == null ? BigDecimal.ZERO : initialReading;
    }

    private BigDecimal existingPreviousReading(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT previous_reading FROM energy_reading WHERE id = ? AND deleted = 0", id);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "抄表记录不存在");
        }
        return decimalValue(rows.getFirst().get("previous_reading"));
    }

    private BigDecimal configuredUnitPrice(String periodMonth, String meterType, BigDecimal fallback) {
        if (periodMonth == null || periodMonth.isBlank() || meterType == null || meterType.isBlank()) {
            return fallback;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT unit_price
                FROM energy_price_setting
                WHERE period_month = ? AND meter_type = ? AND deleted = 0
                LIMIT 1
                """,
                periodMonth.trim(),
                normalizeMeterType(meterType)
        );
        return rows.isEmpty() ? fallback : decimalValue(rows.getFirst().get("unit_price")).setScale(4, RoundingMode.HALF_UP);
    }

    private Map<String, Object> findReadingForBill(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT er.*, m.tenant_id, m.meter_type
                FROM energy_reading er
                JOIN energy_meter m ON m.id = er.meter_id AND m.deleted = 0
                WHERE er.id = ? AND er.deleted = 0
                """,
                id
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "抄表记录不存在");
        }
        return rows.getFirst();
    }

    private Map<String, Object> findLinkedEnergyBill(Long billId, Long readingId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT b.id,
                       b.pay_status AS payStatus,
                       b.paid_amount AS paidAmount
                FROM bill b
                WHERE b.id = ?
                  AND b.source_type = 'ENERGY' AND b.source_id = ?
                  AND b.deleted = 0
                LIMIT 1
                """,
                billId,
                readingId
        );
        if (rows.isEmpty()) {
            throw new BusinessException(400, "未找到关联财务账单，请检查数据");
        }
        return rows.getFirst();
    }

    private List<Map<String, Object>> activeInvoices(Long billId) {
        return jdbcTemplate.queryForList(
                """
                SELECT i.id,
                       i.invoice_status AS invoiceStatus,
                       CASE WHEN f.id IS NULL THEN 0 ELSE 1 END AS hasInvoiceFile
                FROM bill_invoice i
                LEFT JOIN bill_invoice_attachment ia ON ia.invoice_id = i.id AND ia.deleted = 0
                LEFT JOIN file_upload f ON f.id = ia.file_id AND f.deleted = 0
                WHERE i.bill_id = ? AND i.deleted = 0
                LIMIT 1
                """,
                billId
        );
    }

    private void logEnergyBillOperation(
            Long readingId,
            Long billId,
            String action,
            String reason,
            String resultStatus,
            String message,
            Long operatorId,
            String operatorName) {
        jdbcTemplate.update(
                """
                INSERT INTO energy_bill_operation_log (
                  code, reading_id, bill_id, action, reason, result_status, message, operator_id, operator_name
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                JdbcMaps.code("EBL"),
                readingId,
                billId,
                action,
                reason,
                resultStatus,
                message,
                operatorId,
                operatorName
        );
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value);
        if (text.isBlank()) {
            return null;
        }
        return Long.parseLong(text);
    }

    private String normalizeMeterType(String value) {
        String normalized = value == null ? "ELECTRIC" : value.trim().toUpperCase(Locale.ROOT);
        if ("ELECTRICITY".equals(normalized)) {
            normalized = "ELECTRIC";
        }
        if (!METER_TYPES.contains(normalized)) {
            throw new BusinessException(400, "表具类型不正确");
        }
        return normalized;
    }

    private String priceFieldName(String type) {
        return switch (type) {
            case "WATER" -> "waterPrice";
            case "GAS" -> "gasPrice";
            default -> "electricPrice";
        };
    }

    private BigDecimal priceSettingValue(Map<String, Object> body, String type) {
        BigDecimal value = switch (type) {
            case "WATER" -> JdbcMaps.decimal(body, null, "waterPrice", "WATER", "water");
            case "GAS" -> JdbcMaps.decimal(body, null, "gasPrice", "GAS", "gas");
            default -> JdbcMaps.decimal(body, null, "electricPrice", "ELECTRIC", "electric");
        };
        if (value == null) {
            return null;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(400, "能耗单价不能小于0");
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private String normalizeMeterStatus(String value) {
        String normalized = value == null ? "ENABLED" : value.trim().toUpperCase(Locale.ROOT);
        if ("ACTIVE".equals(normalized)) {
            normalized = "ENABLED";
        }
        if (!METER_STATUSES.contains(normalized)) {
            throw new BusinessException(400, "表具状态不正确");
        }
        return normalized;
    }

    private String normalizeAnomalyStatus(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!ANOMALY_STATUSES.contains(normalized)) {
            throw new BusinessException(400, "异常状态不正确");
        }
        return normalized;
    }

    private BigDecimal positiveDecimal(Map<String, Object> body, BigDecimal fallback, String... keys) {
        BigDecimal value = JdbcMaps.decimal(body, fallback, keys);
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "倍率必须大于0");
        }
        return value;
    }

    private BigDecimal nonNegativeDecimal(Map<String, Object> body, BigDecimal fallback, String... keys) {
        BigDecimal value = JdbcMaps.decimal(body, fallback, keys);
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(400, "Reading cannot be negative");
        }
        return value;
    }

    private MeterLocation meterLocation(Map<String, Object> body) {
        Long buildingId = JdbcMaps.longVal(body, "buildingId");
        Long floorId = JdbcMaps.longVal(body, "floorId");
        Long roomId = JdbcMaps.longVal(body, "roomId");
        if (floorId != null && buildingId == null) {
            throw new BusinessException(400, "A floor location must select its building");
        }
        if (roomId != null && (buildingId == null || floorId == null)) {
            throw new BusinessException(400, "A room location must select its building and floor");
        }
        if (buildingId != null) {
            requireAssetReference(
                    "SELECT COUNT(*) FROM building WHERE id = ? AND deleted = 0 AND status IN ('ACTIVE', 'ENABLED')",
                    "Building does not exist or is disabled",
                    buildingId
            );
        }
        if (floorId != null) {
            requireAssetReference(
                    "SELECT COUNT(*) FROM building_floor WHERE id = ? AND building_id = ? AND deleted = 0 AND status IN ('ACTIVE', 'ENABLED')",
                    "Floor does not belong to the selected building",
                    floorId,
                    buildingId
            );
        }
        if (roomId != null) {
            requireAssetReference(
                    "SELECT COUNT(*) FROM building_room WHERE id = ? AND building_id = ? AND floor_id = ? AND deleted = 0",
                    "Room does not belong to the selected floor",
                    roomId,
                    buildingId,
                    floorId
            );
        }
        return new MeterLocation(buildingId, floorId, roomId);
    }

    private void requireAssetReference(String sql, String message, Object... args) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args);
        if (count == null || count == 0) {
            throw new BusinessException(400, message);
        }
    }

    private BigDecimal decimalValue(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(String.valueOf(value));
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private BigDecimal sumUsage(List<Map<String, Object>> rows, String meterType) {
        return rows.stream()
                .filter(row -> meterType.equals(row.get("meterType")))
                .map(row -> decimalValue(row.get("usageAmount")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumAmount(List<Map<String, Object>> rows, String meterType) {
        return rows.stream()
                .filter(row -> meterType.equals(row.get("meterType")))
                .map(row -> decimalValue(row.get("settlementAmount")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumAllUsage(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(row -> decimalValue(row.get("usageAmount")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal aggregateDecimal(Map<String, Map<String, Object>> rows, String meterType, String key) {
        Map<String, Object> row = rows.get(meterType);
        return row == null ? BigDecimal.ZERO : decimalValue(row.get(key));
    }

    private String defaultUnit(String meterType) {
        return "ELECTRIC".equals(meterType) ? "kWh" : "m³";
    }

    private void ensureUpdated(int updated, String message) {
        if (updated == 0) {
            throw new BusinessException(404, message);
        }
    }

    private record QueryParts(String where, List<Object> args) {
    }

    private record MeterLocation(Long buildingId, Long floorId, Long roomId) {
    }

    private record InitialMeterBinding(
            String subjectType,
            Long tenantId,
            Long roomId,
            LocalDate effectiveFrom) {
    }
}
