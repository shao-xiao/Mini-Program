package com.dehui.property.modules.investment.controller;

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
import com.dehui.property.security.AuthInterceptor;
import com.dehui.property.security.AuthPrincipal;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
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
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class InvestmentController {

    private static final String ROOM_COMMERCIALLY_OCCUPIED_SQL = """
            (
                UPPER(COALESCE(r.rent_status, 'VACANT')) NOT IN ('VACANT', 'AVAILABLE')
                OR EXISTS (
                    SELECT 1
                    FROM lease_room active_lr
                    WHERE active_lr.room_id = r.id
                      AND active_lr.status = 'ACTIVE'
                      AND active_lr.occupancy_end_at IS NULL
                      AND active_lr.deleted = 0
                )
                OR EXISTS (
                    SELECT 1
                    FROM contract active_contract
                    WHERE active_contract.deleted = 0
                      AND active_contract.status = 'ACTIVE'
                      AND (
                          EXISTS (
                              SELECT 1
                              FROM contract_room active_cr
                              WHERE active_cr.contract_id = active_contract.id
                                AND active_cr.room_id = r.id
                                AND active_cr.deleted = 0
                          )
                          OR (
                              active_contract.room_id = r.id
                              AND NOT EXISTS (
                                  SELECT 1
                                  FROM contract_room explicit_cr
                                  WHERE explicit_cr.contract_id = active_contract.id
                                    AND explicit_cr.deleted = 0
                              )
                          )
                      )
                )
            )
            """.trim();

    private static final String LISTING_RENTAL_STATUS_SQL =
            "CASE WHEN " + ROOM_COMMERCIALLY_OCCUPIED_SQL
                    + " THEN 'RENTED' ELSE r.rent_status END";

    private static final Map<String, String> CONTENT_ORDER_FIELDS = Map.of(
            "sectionKey", "d.section_key",
            "title", "d.title",
            "sortOrder", "d.sort_order",
            "publishStatus", "d.publish_status",
            "updatedAt", "d.updated_at"
    );

    private static final Map<String, String> LISTING_ORDER_FIELDS = Map.ofEntries(
            Map.entry("title", "l.title"),
            Map.entry("buildingName", "b.name"),
            Map.entry("floorName", "f.floor_no"),
            Map.entry("roomNumber", "r.room_no"),
            Map.entry("area", "r.area"),
            Map.entry("roomType", "r.usage_type"),
            Map.entry("status", LISTING_RENTAL_STATUS_SQL),
            Map.entry("publishStatus", "l.publish_status"),
            Map.entry("recommended", "l.is_recommended"),
            Map.entry("sortOrder", "l.sort_order"),
            Map.entry("updatedAt", "l.updated_at")
    );

    private static final Map<String, String> LEAD_ORDER_FIELDS = Map.of(
            "name", "lead_record.name",
            "companyName", "lead_record.company_name",
            "source", "lead_record.source",
            "desiredArea", "lead_record.desired_area",
            "preferredVisitTime", "lead_record.preferred_visit_time",
            "status", "lead_record.lead_status",
            "createdTime", "lead_record.created_at"
    );

    private static final List<String> SECTION_KEYS = List.of(
            "hero",
            "highlight",
            "policy",
            "introduction",
            "location",
            "contact"
    );

    private static final Map<String, String> SECTION_NAMES = Map.of(
            "hero", "顶部主视觉",
            "highlight", "园区亮点",
            "policy", "招商政策",
            "introduction", "园区介绍",
            "location", "园区地址",
            "contact", "联系方式"
    );
    private static final Set<String> LEAD_STATUSES = Set.of(
            "NEW", "FOLLOWING", "VIEWING_SCHEDULED", "VIEWED", "CONVERTED", "INVALID"
    );
    private static final Set<String> TERMINAL_LEAD_STATUSES = Set.of("CONVERTED", "INVALID");

    private static final Pattern CONTACT_NAME_PATTERN = Pattern.compile("^(?!\\d+$)[\\p{IsHan}a-zA-Z·\\s]{2,20}$");
    private static final Pattern CONTACT_PHONE_PATTERN = Pattern.compile("^(1[3-9]\\d{9}|0\\d{2,3}-?\\d{7,8})$");
    private static final Pattern COMPANY_NAME_PATTERN = Pattern.compile("^(?!\\d+$)[\\p{IsHan}a-zA-Z0-9（）()\\-—\\s]{2,50}$");
    private static final Pattern INTENDED_ROOM_PATTERN = Pattern.compile("^[\\p{IsHan}a-zA-Z0-9（）()\\-—#号座栋层区Ff㎡/\\s]{1,50}$");
    private static final Pattern INTENDED_USE_PATTERN = Pattern.compile("^[\\p{IsHan}a-zA-Z0-9（）()\\-—/\\s]{2,50}$");
    private static final Pattern UNSAFE_TEXT_PATTERN = Pattern.compile("(<[^>]*>|</?script|javascript:|onerror\\s*=|onclick\\s*=|--|/\\*|\\*/|;)", Pattern.CASE_INSENSITIVE);
    private static final long MAX_LISTING_IMAGE_SIZE = 20L * 1024L * 1024L;

    private final JdbcTemplate jdbcTemplate;
    private final CustomFieldQueryService customFieldQueryService;
    private final CustomFieldProjectionService customFieldProjectionService;

    public InvestmentController(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null, null);
    }

    @Value("${dehui.file.upload-dir:uploads}")
    private String uploadDir;

    @GetMapping({"/investment/displays", "/investment/contents"})
    public ApiResponse<PageResponse<Map<String, Object>>> displays(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sectionKey,
            @RequestParam(required = false) String publishStatus,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDirection,
            @RequestParam(required = false) String sorts,
            @RequestParam(required = false) String groupBy,
            @RequestParam(required = false) String customFilters,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        List<Object> args = new ArrayList<>();
        String where = contentWhere(keyword, sectionKey, publishStatus, args);
        CompiledQuery customQuery = compileCustomQuery(
                "investment-contents", customFilters, groupBy, sorts,
                CONTENT_ORDER_FIELDS, sortField, sortDirection, "d.sort_order", "d.id"
        );
        if (customQuery != null && customQuery.hasPredicates()) {
            where += " AND " + customQuery.predicateSql();
            args.addAll(customQuery.predicateArguments());
        }
        String orderBy = customQuery != null && customQuery.hasOrderings()
                ? String.join(", ", customQuery.orderExpressions())
                : workspaceOrderBy(
                CONTENT_ORDER_FIELDS, groupBy, sorts, sortField, sortDirection,
                "d.section_key ASC, d.sort_order ASC, d.id DESC", "d.sort_order", "d.id"
        );
        PageResponse<Map<String, Object>> result = JdbcPagination.query(
                jdbcTemplate,
                """
                SELECT d.id,
                       d.code,
                       d.section_key AS sectionKey,
                       CASE d.section_key
                         WHEN 'hero' THEN '顶部主视觉'
                         WHEN 'highlight' THEN '园区亮点'
                         WHEN 'policy' THEN '招商政策'
                          WHEN 'introduction' THEN '园区介绍'
                          WHEN 'location' THEN '园区地址'
                          WHEN 'contact' THEN '联系方式'
                          ELSE d.section_key
                        END AS sectionName,
                       d.title,
                       d.subtitle,
                       d.content,
                       d.image_url AS imageUrl,
                       d.public_visible AS publicVisible,
                       d.effective_at AS effectiveAt,
                       d.expires_at AS expiresAt,
                       d.publish_status AS publishStatus,
                       d.publish_status AS status,
                       d.sort_order AS sortOrder,
                       d.status AS recordStatus,
                       d.remark,
                       d.updated_at AS updatedAt
                FROM investment_display d
                """ + where + " ORDER BY " + orderBy,
                "SELECT COUNT(*) FROM investment_display d" + where,
                args,
                customQuery == null ? List.of() : customQuery.orderArguments(),
                page,
                pageSize
        );
        if (customFieldProjectionService != null) {
            result = customFieldProjectionService.attach("investment-contents", result);
        }
        return ApiResponse.success(result);
    }

    public ApiResponse<PageResponse<Map<String, Object>>> displays(
            String keyword,
            String sectionKey,
            String publishStatus,
            String sortField,
            String sortDirection,
            String sorts,
            String groupBy,
            Integer page,
            Integer pageSize) {
        return displays(
                keyword, sectionKey, publishStatus, sortField, sortDirection,
                sorts, groupBy, null, page, pageSize
        );
    }

    public ApiResponse<PageResponse<Map<String, Object>>> displays(Integer page, Integer pageSize) {
        return displays(null, null, null, null, null, null, null, null, page, pageSize);
    }

    @PostMapping("/investment/contents")
    @Transactional
    public ApiResponse<Void> createContent(@RequestBody Map<String, Object> body) {
        validateEffectiveWindow(body);
        String requestedStatus = publishStatus(body, "DRAFT");
        jdbcTemplate.update(
                """
                INSERT INTO investment_display (
                    code, section_key, title, subtitle, content, image_url,
                    public_visible, effective_at, expires_at,
                    publish_status, sort_order, status, created_by, updated_by, remark
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                """,
                JdbcMaps.code("INV"),
                sectionKey(body),
                JdbcMaps.requiredStr(body, "Title is required", "title"),
                JdbcMaps.strOr(body, "", "subtitle"),
                JdbcMaps.strOr(body, "", "content"),
                JdbcMaps.strOr(body, "", "imageUrl", "image"),
                Boolean.TRUE.equals(JdbcMaps.bool(body, true, "publicVisible")) ? 1 : 0,
                JdbcMaps.datetime(body, null, "effectiveAt"),
                JdbcMaps.datetime(body, null, "expiresAt"),
                "PUBLISHED".equals(requestedStatus) ? "DRAFT" : requestedStatus,
                JdbcMaps.intVal(body, 0, "sortOrder"),
                0L,
                0L,
                JdbcMaps.str(body, "remark", "summary")
        );
        if ("PUBLISHED".equals(requestedStatus)) {
            publishContent(jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class));
        }
        return ApiResponse.success();
    }

    @PutMapping("/investment/contents/{id}")
    @Transactional
    public ApiResponse<Void> updateContent(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        validateEffectiveWindow(body);
        String requestedStatus = publishStatus(body, "DRAFT");
        int updated = jdbcTemplate.update(
                """
                UPDATE investment_display
                SET section_key = ?, title = ?, subtitle = ?, content = ?, image_url = ?,
                    public_visible = ?, effective_at = ?, expires_at = ?,
                    publish_status = ?, sort_order = ?, updated_by = ?, remark = ?
                WHERE id = ? AND deleted = 0
                """,
                sectionKey(body),
                JdbcMaps.requiredStr(body, "Title is required", "title"),
                JdbcMaps.strOr(body, "", "subtitle"),
                JdbcMaps.strOr(body, "", "content"),
                JdbcMaps.strOr(body, "", "imageUrl", "image"),
                Boolean.TRUE.equals(JdbcMaps.bool(body, true, "publicVisible")) ? 1 : 0,
                JdbcMaps.datetime(body, null, "effectiveAt"),
                JdbcMaps.datetime(body, null, "expiresAt"),
                "PUBLISHED".equals(requestedStatus) ? "DRAFT" : requestedStatus,
                JdbcMaps.intVal(body, 0, "sortOrder"),
                0L,
                JdbcMaps.str(body, "remark", "summary"),
                id
        );
        ensureUpdated(updated, "Investment content not found");
        if ("PUBLISHED".equals(requestedStatus)) {
            publishContent(id);
        }
        return ApiResponse.success();
    }

    @PostMapping("/investment/contents/{id}/publish")
    @Transactional
    public ApiResponse<Void> publishContent(@PathVariable Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT section_key FROM investment_display WHERE id = ? AND deleted = 0",
                id
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "Investment content not found");
        }
        String sectionKey = normalizeSectionKey(rows.getFirst().get("section_key"));
        jdbcTemplate.update(
                """
                UPDATE investment_display
                SET publish_status = 'DISABLED', updated_by = ?
                WHERE section_key = ? AND id <> ? AND publish_status = 'PUBLISHED' AND deleted = 0
                """,
                0L,
                sectionKey,
                id
        );
        ensureUpdated(jdbcTemplate.update(
                "UPDATE investment_display SET publish_status = 'PUBLISHED', updated_by = ? WHERE id = ? AND deleted = 0",
                0L,
                id
        ), "Investment content not found");
        return ApiResponse.success();
    }

    @PostMapping("/investment/contents/{id}/disable")
    public ApiResponse<Void> disableContent(@PathVariable Long id) {
        ensureUpdated(jdbcTemplate.update("UPDATE investment_display SET publish_status = 'DISABLED', updated_by = ? WHERE id = ? AND deleted = 0", 0L, id), "Investment content not found");
        return ApiResponse.success();
    }

    @DeleteMapping("/investment/contents/{id}")
    public ApiResponse<Void> deleteContent(@PathVariable Long id) {
        ensureUpdated(jdbcTemplate.update("UPDATE investment_display SET deleted = 1, updated_by = ? WHERE id = ? AND deleted = 0", 0L, id), "Investment content not found");
        return ApiResponse.success();
    }

    @GetMapping("/investment/contents/preview")
    public ApiResponse<Map<String, Object>> contentPreview() {
        return ApiResponse.success(groupedContent(false));
    }

    @GetMapping("/investment/leads")
    public ApiResponse<PageResponse<Map<String, Object>>> leads(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDirection,
            @RequestParam(required = false) String sorts,
            @RequestParam(required = false) String groupBy,
            @RequestParam(required = false) String customFilters,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        List<Object> args = new ArrayList<>();
        String where = leadWhere(keyword, status, source, args);
        CompiledQuery customQuery = compileCustomQuery(
                "investment-leads", customFilters, groupBy, sorts,
                LEAD_ORDER_FIELDS, sortField, sortDirection, "lead_record.updated_at", "lead_record.id"
        );
        if (customQuery != null && customQuery.hasPredicates()) {
            where += " AND " + customQuery.predicateSql();
            args.addAll(customQuery.predicateArguments());
        }
        String orderBy = customQuery != null && customQuery.hasOrderings()
                ? String.join(", ", customQuery.orderExpressions())
                : workspaceOrderBy(
                LEAD_ORDER_FIELDS, groupBy, sorts, sortField, sortDirection,
                "lead_record.updated_at DESC, lead_record.id DESC", "lead_record.updated_at", "lead_record.id"
        );
        PageResponse<Map<String, Object>> result = JdbcPagination.query(
                jdbcTemplate,
                """
                SELECT lead_record.id, lead_record.code, lead_record.name, lead_record.phone,
                       lead_record.company_name AS companyName, lead_record.desired_area AS desiredArea,
                       lead_record.intended_room AS intendedRoom, lead_record.intended_room AS roomNumber,
                       lead_record.room_id AS roomId, lead_record.listing_id AS listingId,
                       lead_record.mobile_user_id AS mobileUserId,
                       lead_record.listing_title_snapshot AS listingTitleSnapshot,
                       lead_record.building_name_snapshot AS buildingNameSnapshot,
                       lead_record.floor_snapshot AS floorSnapshot,
                       lead_record.room_code_snapshot AS roomCodeSnapshot,
                       lead_record.area_snapshot AS areaSnapshot,
                       lead_record.intended_use AS intendedUse,
                       lead_record.preferred_visit_time AS preferredVisitTime,
                       lead_record.source, lead_record.lead_status AS status, lead_record.remark,
                       lead_record.status_changed_at AS statusChangedAt,
                       lead_record.created_at AS createdTime, lead_record.updated_at AS updatedAt,
                       mobile_record.user_type AS mobileUserType,
                       mobile_record.status AS mobileUserStatus,
                       CASE
                         WHEN mobile_record.phone IS NULL OR mobile_record.phone = '' THEN ''
                         ELSE CONCAT(LEFT(mobile_record.phone, 3), '****', RIGHT(mobile_record.phone, 4))
                       END AS mobilePhoneMasked
                 FROM investment_lead lead_record
                 LEFT JOIN mobile_user mobile_record
                   ON mobile_record.id = lead_record.mobile_user_id AND mobile_record.deleted = 0
                 """ + where + " ORDER BY " + orderBy,
                "SELECT COUNT(*) FROM investment_lead lead_record" + where,
                args,
                customQuery == null ? List.of() : customQuery.orderArguments(),
                page,
                pageSize
        );
        if (customFieldProjectionService != null) {
            result = customFieldProjectionService.attach("investment-leads", result);
        }
        return ApiResponse.success(result);
    }

    public ApiResponse<PageResponse<Map<String, Object>>> leads(
            String keyword,
            String status,
            String source,
            String sortField,
            String sortDirection,
            String sorts,
            String groupBy,
            Integer page,
            Integer pageSize) {
        return leads(keyword, status, source, sortField, sortDirection, sorts, groupBy, null, page, pageSize);
    }

    @GetMapping("/investment/leads/summary")
    public ApiResponse<Map<String, Object>> leadSummary(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String customFilters) {
        List<Object> args = new ArrayList<>();
        String where = leadWhere(keyword, status, source, args);
        CompiledQuery customQuery = compileCustomQuery(
                "investment-leads", customFilters, null, null,
                LEAD_ORDER_FIELDS, null, null, "lead_record.updated_at", "lead_record.id"
        );
        if (customQuery != null && customQuery.hasPredicates()) {
            where += " AND " + customQuery.predicateSql();
            args.addAll(customQuery.predicateArguments());
        }
        return ApiResponse.success(jdbcTemplate.queryForMap(
                """
                SELECT COUNT(*) AS totalCount,
                       COALESCE(SUM(CASE WHEN lead_record.lead_status = 'NEW' THEN 1 ELSE 0 END), 0) AS newCount,
                       COALESCE(SUM(CASE WHEN lead_record.lead_status IN ('FOLLOWING', 'CONTACTED', 'VIEWING_SCHEDULED', 'VIEWED', 'NEGOTIATING') THEN 1 ELSE 0 END), 0) AS followingCount,
                       COALESCE(SUM(CASE WHEN lead_record.lead_status IN ('CONVERTED', 'CONVERTED_TENANT', 'CONVERTED_CONTRACT') THEN 1 ELSE 0 END), 0) AS convertedCount
                FROM investment_lead lead_record
                """ + where,
                args.toArray()
        ));
    }

    public ApiResponse<Map<String, Object>> leadSummary(String keyword, String status, String source) {
        return leadSummary(keyword, status, source, null);
    }

    @GetMapping("/investment/leads/{id}")
    public ApiResponse<Map<String, Object>> leadDetail(@PathVariable Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT lead_record.id,
                       lead_record.code,
                       lead_record.name,
                       lead_record.phone,
                       lead_record.company_name AS companyName,
                       lead_record.desired_area AS desiredArea,
                       lead_record.intended_room AS intendedRoom,
                       lead_record.room_id AS roomId,
                       lead_record.listing_id AS listingId,
                       lead_record.mobile_user_id AS mobileUserId,
                       lead_record.listing_title_snapshot AS listingTitleSnapshot,
                       lead_record.building_name_snapshot AS buildingNameSnapshot,
                       lead_record.floor_snapshot AS floorSnapshot,
                       lead_record.room_code_snapshot AS roomCodeSnapshot,
                       lead_record.area_snapshot AS areaSnapshot,
                       lead_record.intended_use AS intendedUse,
                       lead_record.preferred_visit_time AS preferredVisitTime,
                       lead_record.source,
                       lead_record.lead_status AS status,
                       lead_record.status_changed_at AS statusChangedAt,
                       lead_record.remark,
                       lead_record.created_at AS createdTime,
                       mobile_record.user_type AS mobileUserType,
                       mobile_record.status AS mobileUserStatus,
                       CASE
                         WHEN mobile_record.phone IS NULL OR mobile_record.phone = '' THEN ''
                         ELSE CONCAT(LEFT(mobile_record.phone, 3), '****', RIGHT(mobile_record.phone, 4))
                       END AS mobilePhoneMasked
                FROM investment_lead lead_record
                LEFT JOIN mobile_user mobile_record
                  ON mobile_record.id = lead_record.mobile_user_id AND mobile_record.deleted = 0
                WHERE lead_record.id = ? AND lead_record.deleted = 0
                """,
                id
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "Investment lead not found");
        }
        Map<String, Object> detail = new LinkedHashMap<>(rows.getFirst());
        detail.put("followRecords", followRecords(id));
        detail.put("statusHistory", jdbcTemplate.queryForList(
                """
                SELECT id,
                       from_status AS fromStatus,
                       to_status AS toStatus,
                       operator_id AS operatorId,
                       changed_at AS changedAt,
                       remark
                FROM investment_lead_status_history
                WHERE lead_id = ? AND deleted = 0
                ORDER BY changed_at DESC, id DESC
                """,
                id
        ));
        return ApiResponse.success(detail);
    }

    @GetMapping("/investment/leads/{id}/follow-records")
    public ApiResponse<List<Map<String, Object>>> leadFollowRecords(@PathVariable Long id) {
        ensureLeadExists(id);
        return ApiResponse.success(followRecords(id));
    }

    @PostMapping("/investment/leads/{id}/follow-records")
    public ApiResponse<Void> addLeadFollowRecord(
            @PathVariable Long id,
            @RequestAttribute(value = AuthInterceptor.PRINCIPAL_ATTRIBUTE, required = false) AuthPrincipal principal,
            @RequestBody Map<String, Object> body) {
        ensureLeadExists(id);
        Long operatorId = operatorId(principal);
        jdbcTemplate.update(
                """
                INSERT INTO investment_follow_record (
                    lead_id, follower_id, follow_type, content, next_follow_at,
                    status, created_by, updated_by
                )
                VALUES (?, ?, ?, ?, ?, 'ENABLED', ?, ?)
                """,
                id,
                operatorId,
                normalizedFollowType(body),
                JdbcMaps.requiredStr(body, "跟进内容不能为空", "content"),
                JdbcMaps.datetime(body, null, "nextFollowAt"),
                operatorId,
                operatorId
        );
        return ApiResponse.success();
    }

    @PatchMapping("/investment/leads/{id}/status")
    @Transactional
    public ApiResponse<Void> updateLeadStatus(
            @PathVariable Long id,
            @RequestAttribute(value = AuthInterceptor.PRINCIPAL_ATTRIBUTE, required = false) AuthPrincipal principal,
            @RequestBody Map<String, Object> body) {
        transitionLead(id, normalizedLeadStatus(body), operatorId(principal), JdbcMaps.str(body, "remark"));
        return ApiResponse.success();
    }

    public ApiResponse<Void> updateLeadStatus(Long id, Map<String, Object> body) {
        transitionLead(id, normalizedLeadStatus(body), 0L, JdbcMaps.str(body, "remark"));
        return ApiResponse.success();
    }

    @DeleteMapping("/investment/leads/{id}")
    public ApiResponse<Void> deleteLead(@PathVariable Long id) {
        Map<String, Object> lead = ensureLeadExists(id);
        if (!"INVALID".equals(str(lead.get("lead_status")).toUpperCase(Locale.ROOT))) {
            throw new BusinessException(409, "仅无效线索允许归档");
        }
        ensureUpdated(jdbcTemplate.update(
                "UPDATE investment_lead SET deleted = 1, updated_by = ? WHERE id = ? AND deleted = 0",
                0L,
                id
        ), "Investment lead not found");
        return ApiResponse.success();
    }

    @PostMapping("/investment/leads/{id}/convert-tenant")
    @Transactional
    public ApiResponse<Void> convertTenant(
            @PathVariable Long id,
            @RequestAttribute(value = AuthInterceptor.PRINCIPAL_ATTRIBUTE, required = false) AuthPrincipal principal) {
        transitionLead(id, "CONVERTED", operatorId(principal), "招商机会已转化；未自动创建租户");
        return ApiResponse.success();
    }

    @PostMapping("/investment/leads/{id}/convert-contract")
    @Transactional
    public ApiResponse<Void> convertContract(
            @PathVariable Long id,
            @RequestAttribute(value = AuthInterceptor.PRINCIPAL_ATTRIBUTE, required = false) AuthPrincipal principal) {
        transitionLead(id, "CONVERTED", operatorId(principal), "招商机会已转化；未自动创建合同");
        return ApiResponse.success();
    }

    @GetMapping({"/investment/listings", "/admin/investment/listings"})
    public ApiResponse<PageResponse<Map<String, Object>>> listings(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String publishStatus,
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long floorId,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDirection,
            @RequestParam(required = false) String sorts,
            @RequestParam(required = false) String groupBy,
            @RequestParam(required = false) String customFilters,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        List<Object> args = new ArrayList<>();
        String where = listingWhere(keyword, publishStatus, buildingId, floorId, args, false);
        CompiledQuery customQuery = compileCustomQuery(
                "investment-listings", customFilters, groupBy, sorts,
                LISTING_ORDER_FIELDS, sortField, sortDirection, "l.updated_at", "l.id"
        );
        if (customQuery != null && customQuery.hasPredicates()) {
            where += " AND " + customQuery.predicateSql();
            args.addAll(customQuery.predicateArguments());
        }
        String orderBy = customQuery != null && customQuery.hasOrderings()
                ? String.join(", ", customQuery.orderExpressions())
                : workspaceOrderBy(
                LISTING_ORDER_FIELDS, groupBy, sorts, sortField, sortDirection,
                "l.sort_order ASC, l.updated_at DESC, l.id DESC", "l.updated_at", "l.id"
        );
        PageResponse<Map<String, Object>> result = JdbcPagination.query(
                jdbcTemplate,
                listingSelectSql() + where + " ORDER BY " + orderBy,
                """
                SELECT COUNT(*)
                FROM investment_room_listing l
                JOIN building_room r ON r.id = l.room_id AND r.deleted = 0
                LEFT JOIN building b ON b.id = r.building_id
                LEFT JOIN building_floor f ON f.id = r.floor_id
                """ + where,
                args,
                customQuery == null ? List.of() : customQuery.orderArguments(),
                page,
                pageSize
        );
        if (customFieldProjectionService != null) {
            result = customFieldProjectionService.attach("investment-listings", result);
        }
        return ApiResponse.success(result);
    }

    public ApiResponse<PageResponse<Map<String, Object>>> listings(
            String keyword,
            String publishStatus,
            Long buildingId,
            Long floorId,
            String sortField,
            String sortDirection,
            String sorts,
            String groupBy,
            Integer page,
            Integer pageSize) {
        return listings(
                keyword, publishStatus, buildingId, floorId, sortField, sortDirection,
                sorts, groupBy, null, page, pageSize
        );
    }

    @GetMapping({"/investment/listings/summary", "/admin/investment/listings/summary"})
    public ApiResponse<Map<String, Object>> listingSummary(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String publishStatus,
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long floorId,
            @RequestParam(required = false) String customFilters) {
        List<Object> args = new ArrayList<>();
        String where = listingWhere(keyword, publishStatus, buildingId, floorId, args, false);
        CompiledQuery customQuery = compileCustomQuery(
                "investment-listings", customFilters, null, null,
                LISTING_ORDER_FIELDS, null, null, "l.updated_at", "l.id"
        );
        if (customQuery != null && customQuery.hasPredicates()) {
            where += " AND " + customQuery.predicateSql();
            args.addAll(customQuery.predicateArguments());
        }
        return ApiResponse.success(jdbcTemplate.queryForMap(
                """
                SELECT COUNT(*) AS totalCount,
                       COALESCE(SUM(CASE WHEN l.publish_status = 'PUBLISHED' THEN 1 ELSE 0 END), 0) AS publishedCount,
                       COALESCE(SUM(CASE WHEN l.publish_status IN ('DRAFT', 'OFFLINE') THEN 1 ELSE 0 END), 0) AS draftCount,
                       COALESCE(SUM(CASE WHEN r.usage_type = 'OFFICE' THEN 1 ELSE 0 END), 0) AS officeCount,
                       COALESCE(SUM(CASE WHEN l.is_recommended = 1 THEN 1 ELSE 0 END), 0) AS recommendedCount
                FROM investment_room_listing l
                JOIN building_room r ON r.id = l.room_id AND r.deleted = 0
                LEFT JOIN building b ON b.id = r.building_id
                LEFT JOIN building_floor f ON f.id = r.floor_id
                """ + where,
                args.toArray()
        ));
    }

    public ApiResponse<Map<String, Object>> listingSummary(
            String keyword,
            String publishStatus,
            Long buildingId,
            Long floorId
    ) {
        return listingSummary(keyword, publishStatus, buildingId, floorId, null);
    }

    @GetMapping({"/investment/listings/{id}", "/admin/investment/listings/{id}"})
    public ApiResponse<Map<String, Object>> listingDetail(@PathVariable Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                listingSelectSql() + " WHERE l.id = ? AND l.deleted = 0",
                id
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "Investment listing not found");
        }
        Map<String, Object> detail = new LinkedHashMap<>(rows.getFirst());
        detail.put("images", listingImages(id));
        return ApiResponse.success(detail);
    }

    @PostMapping({"/investment/listings", "/admin/investment/listings"})
    public ApiResponse<Long> createListing(@RequestBody Map<String, Object> body) {
        validateEffectiveWindow(body);
        Long roomId = JdbcMaps.requiredLong(body, "Room is required", "roomId", "room_id");
        ensureRoomExists(roomId);
        BigDecimal displayAreaSnapshot = roomArea(roomId);
        if ("PUBLISHED".equals(listingPublishStatus(body, "DRAFT"))) {
            throw new BusinessException(400, "请先保存草稿并上传图片后再上架");
        }
        jdbcTemplate.update(
                """
                INSERT INTO investment_room_listing
                  (room_id, display_area_snapshot, title, subtitle, description, highlights, tags,
                   rent_desc, property_fee_desc, cover_image_url, publish_status, is_recommended,
                   is_reservable, sort_order, effective_at, expires_at, status, created_by, updated_by, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                """,
                roomId,
                displayAreaSnapshot,
                JdbcMaps.str(body, "title"),
                JdbcMaps.str(body, "subtitle"),
                JdbcMaps.str(body, "description"),
                JdbcMaps.str(body, "highlights"),
                JdbcMaps.str(body, "tags"),
                JdbcMaps.str(body, "rentDesc", "rent_desc"),
                JdbcMaps.str(body, "propertyFeeDesc", "property_fee_desc"),
                JdbcMaps.str(body, "coverImageUrl", "cover_image_url"),
                listingPublishStatus(body, "DRAFT"),
                Boolean.TRUE.equals(JdbcMaps.bool(body, false, "recommended", "isRecommended")) ? 1 : 0,
                Boolean.TRUE.equals(JdbcMaps.bool(body, true, "reservable", "isReservable")) ? 1 : 0,
                JdbcMaps.intVal(body, 0, "sortOrder", "sort_order"),
                JdbcMaps.datetime(body, null, "effectiveAt"),
                JdbcMaps.datetime(body, null, "expiresAt"),
                0L,
                0L,
                JdbcMaps.str(body, "remark")
        );
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return ApiResponse.success(id);
    }

    @PutMapping({"/investment/listings/{id}", "/admin/investment/listings/{id}"})
    public ApiResponse<Void> updateListing(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        validateEffectiveWindow(body);
        Long roomId = JdbcMaps.requiredLong(body, "Room is required", "roomId", "room_id");
        ensureRoomExists(roomId);
        BigDecimal displayAreaSnapshot = roomArea(roomId);
        String publishStatus = listingPublishStatus(body, "DRAFT");
        if ("PUBLISHED".equals(publishStatus)) {
            ensureListingPublishable(
                    id,
                    roomId,
                    JdbcMaps.str(body, "title"),
                    JdbcMaps.str(body, "coverImageUrl", "cover_image_url")
            );
        }
        ensureUpdated(jdbcTemplate.update(
                """
                UPDATE investment_room_listing
                SET room_id = ?,
                    display_area_snapshot = ?,
                    title = ?,
                    subtitle = ?,
                    description = ?,
                    highlights = ?,
                    tags = ?,
                    rent_desc = ?,
                    property_fee_desc = ?,
                    cover_image_url = ?,
                    publish_status = ?,
                    is_recommended = ?,
                    is_reservable = ?,
                    sort_order = ?,
                    effective_at = ?,
                    expires_at = ?,
                    updated_by = ?,
                    remark = ?
                WHERE id = ? AND deleted = 0
                """,
                roomId,
                displayAreaSnapshot,
                JdbcMaps.str(body, "title"),
                JdbcMaps.str(body, "subtitle"),
                JdbcMaps.str(body, "description"),
                JdbcMaps.str(body, "highlights"),
                JdbcMaps.str(body, "tags"),
                JdbcMaps.str(body, "rentDesc", "rent_desc"),
                JdbcMaps.str(body, "propertyFeeDesc", "property_fee_desc"),
                JdbcMaps.str(body, "coverImageUrl", "cover_image_url"),
                publishStatus,
                Boolean.TRUE.equals(JdbcMaps.bool(body, false, "recommended", "isRecommended")) ? 1 : 0,
                Boolean.TRUE.equals(JdbcMaps.bool(body, true, "reservable", "isReservable")) ? 1 : 0,
                JdbcMaps.intVal(body, 0, "sortOrder", "sort_order"),
                JdbcMaps.datetime(body, null, "effectiveAt"),
                JdbcMaps.datetime(body, null, "expiresAt"),
                0L,
                JdbcMaps.str(body, "remark"),
                id
        ), "Investment listing not found");
        return ApiResponse.success();
    }

    @DeleteMapping({"/investment/listings/{id}", "/admin/investment/listings/{id}"})
    public ApiResponse<Void> deleteListing(@PathVariable Long id) {
        ensureUpdated(jdbcTemplate.update("UPDATE investment_room_listing SET deleted = 1, updated_by = ? WHERE id = ? AND deleted = 0", 0L, id), "Investment listing not found");
        jdbcTemplate.update("UPDATE investment_room_image SET deleted = 1, updated_by = ? WHERE listing_id = ? AND deleted = 0", 0L, id);
        return ApiResponse.success();
    }

    @PostMapping({"/investment/listings/{id}/publish", "/admin/investment/listings/{id}/publish"})
    public ApiResponse<Void> publishListing(@PathVariable Long id) {
        Map<String, Object> listing = ensureListingExists(id);
        ensureListingPublishable(
                id,
                longValue(listing.get("room_id")),
                str(listing.get("title")),
                str(listing.get("cover_image_url"))
        );
        ensureUpdated(jdbcTemplate.update("UPDATE investment_room_listing SET publish_status = 'PUBLISHED', updated_by = ? WHERE id = ? AND deleted = 0", 0L, id), "Investment listing not found");
        return ApiResponse.success();
    }

    @PostMapping({"/investment/listings/{id}/offline", "/admin/investment/listings/{id}/offline"})
    public ApiResponse<Void> offlineListing(@PathVariable Long id) {
        ensureUpdated(jdbcTemplate.update("UPDATE investment_room_listing SET publish_status = 'OFFLINE', updated_by = ? WHERE id = ? AND deleted = 0", 0L, id), "Investment listing not found");
        return ApiResponse.success();
    }

    @GetMapping({"/investment/listings/{id}/images", "/admin/investment/listings/{id}/images"})
    public ApiResponse<List<Map<String, Object>>> listingImagesEndpoint(@PathVariable Long id) {
        ensureListingExists(id);
        return ApiResponse.success(listingImages(id));
    }

    @PostMapping({"/investment/listings/{id}/images", "/admin/investment/listings/{id}/images"})
    public ApiResponse<Map<String, Object>> uploadListingImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "GALLERY") String imageType,
            @RequestParam(defaultValue = "0") Integer sortOrder) {
        Map<String, Object> listing = ensureListingExists(id);
        String url = saveListingImageFile(file);
        String normalizedType = "COVER".equalsIgnoreCase(imageType) ? "COVER" : "GALLERY";
        jdbcTemplate.update(
                """
                INSERT INTO investment_room_image (listing_id, room_id, image_url, image_type, sort_order, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                listing.get("room_id"),
                url,
                normalizedType,
                sortOrder == null ? 0 : sortOrder,
                0L,
                0L
        );
        Long imageId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if ("COVER".equals(normalizedType)) {
            setImageAsCover(imageId);
        }
        return ApiResponse.success(Map.of("id", imageId, "imageUrl", url, "imageType", normalizedType));
    }

    @DeleteMapping({"/investment/listings/images/{imageId}", "/admin/investment/listings/images/{imageId}"})
    public ApiResponse<Void> deleteListingImage(@PathVariable Long imageId) {
        Map<String, Object> image = ensureImageExists(imageId);
        ensureUpdated(jdbcTemplate.update("UPDATE investment_room_image SET deleted = 1, updated_by = ? WHERE id = ? AND deleted = 0", 0L, imageId), "Image not found");
        clearCoverIfNeeded(image);
        return ApiResponse.success();
    }

    @PostMapping({"/investment/listings/images/{imageId}/cover", "/admin/investment/listings/images/{imageId}/cover"})
    public ApiResponse<Void> setListingImageCover(@PathVariable Long imageId) {
        setImageAsCover(imageId);
        return ApiResponse.success();
    }

    @GetMapping({"/investment/rooms/candidates", "/admin/investment/rooms/candidates"})
    public ApiResponse<PageResponse<Map<String, Object>>> listingRoomCandidates(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long floorId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE r.deleted = 0 ");
        if (buildingId != null) {
            where.append(" AND r.building_id = ? ");
            args.add(buildingId);
        }
        if (floorId != null) {
            where.append(" AND r.floor_id = ? ");
            args.add(floorId);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND r.rent_status = ? ");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append("""
             AND (r.room_no LIKE ? OR r.code LIKE ? OR r.remark LIKE ? OR b.name LIKE ? OR f.name LIKE ?)
            """);
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        return ApiResponse.success(JdbcPagination.query(
                jdbcTemplate,
                """
                SELECT r.id,
                       r.code AS roomCode,
                       b.id AS buildingId,
                       b.name AS buildingName,
                       f.id AS floorId,
                       f.name AS floorName,
                       f.floor_no AS floorNo,
                       r.room_no AS roomNumber,
                       r.room_no AS roomName,
                       r.area,
                       r.usage_type AS roomType,
                       r.rent_status AS status,
                       EXISTS(SELECT 1 FROM investment_room_listing l WHERE l.room_id = r.id AND l.deleted = 0) AS listed
                FROM building_room r
                LEFT JOIN building b ON b.id = r.building_id
                LEFT JOIN building_floor f ON f.id = r.floor_id
                """ + where + """
                ORDER BY b.name ASC, f.floor_no ASC, r.room_no ASC
                """,
                """
                SELECT COUNT(*)
                FROM building_room r
                LEFT JOIN building b ON b.id = r.building_id
                LEFT JOIN building_floor f ON f.id = r.floor_id
                """ + where,
                args,
                page,
                pageSize
        ));
    }

    @GetMapping({
            "/mobile/investment/showcase",
            "/mobile/investment/overview",
            "/mobile/investment-content",
            "/miniapp/investment-content"
    })
    public ApiResponse<Map<String, Object>> mobileOverview() {
        Map<String, Object> grouped = groupedContent(true);
        appendLegacyMiniProgramFields(grouped);
        return ApiResponse.success(grouped);
    }

    @GetMapping({"/mobile/investment/listings", "/mobile/investment/rooms"})
    public ApiResponse<List<Map<String, Object>>> mobileRooms() {
        List<Object> args = new ArrayList<>();
        String where = listingWhere(null, "PUBLISHED", null, null, args, true);
        List<Map<String, Object>> rooms = jdbcTemplate.queryForList(
                listingSelectSql() + where + """
                ORDER BY l.is_recommended DESC, l.sort_order ASC, l.updated_at DESC, l.id DESC
                """,
                args.toArray()
        );
        return ApiResponse.success(publicListingRows(withListingImages(rooms)));
    }

    @GetMapping({"/mobile/investment/listings/{id}", "/mobile/investment/rooms/{id}"})
    public ApiResponse<Map<String, Object>> mobileRoomDetail(@PathVariable Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                listingSelectSql() + """
                WHERE l.id = ?
                  AND l.deleted = 0
                  AND l.publish_status = 'PUBLISHED'
                  AND l.status IN ('ACTIVE', 'ENABLED')
                  AND r.deleted = 0
                  AND r.status IN ('ACTIVE', 'ENABLED')
                  AND (l.effective_at IS NULL OR l.effective_at <= NOW())
                  AND (l.expires_at IS NULL OR l.expires_at > NOW())
                  AND NOT %s
                """.formatted(ROOM_COMMERCIALLY_OCCUPIED_SQL),
                id
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "Investment room not found");
        }
        Map<String, Object> detail = new LinkedHashMap<>(rows.getFirst());
        attachListingImages(detail);
        return ApiResponse.success(publicListing(detail));
    }

    @PostMapping({"/mobile/investment/appointments", "/mobile/investment/leads"})
    @Transactional
    public ApiResponse<Void> mobileLead(
            @RequestAttribute(value = AuthInterceptor.PRINCIPAL_ATTRIBUTE, required = false) AuthPrincipal principal,
            @RequestBody Map<String, Object> body) {
        if (principal == null) {
            throw BusinessException.unauthorized("请先建立有效微信会话");
        }
        String contactName = validatedContactName(body);
        String companyName = validatedCompanyName(body);
        BigDecimal desiredArea = validatedDesiredArea(body);
        String intendedUse = validatedIntendedUse(body);
        String remark = validatedRemark(body);
        Long mobileUserId = principal.userId();
        Map<String, Object> mobileUser = verifiedMobileUser(mobileUserId);
        Long listingId = JdbcMaps.requiredLong(body, "请选择有效招商房源", "listingId", "listing_id");
        Map<String, Object> listing = appointmentListing(listingId);
        ensureAppointmentRateLimit(mobileUserId, listingId);

        String preferredVisitTime = JdbcMaps.requiredStr(
                body,
                "请选择期望看房时间",
                "preferredVisitTime"
        ).replace('T', ' ');
        String roomSnapshot = String.join(" / ", List.of(
                str(listing.get("buildingName")),
                str(listing.get("floorName")),
                str(listing.get("roomNumber"))
        ).stream().filter(value -> !value.isBlank()).toList());
        BigDecimal areaSnapshot = decimalValue(
                listing.get("displayAreaSnapshot"),
                decimalValue(listing.get("area"), BigDecimal.ZERO)
        );
        jdbcTemplate.update(
                """
                INSERT INTO investment_lead (
                    code, name, phone, company_name, intended_room, room_id, listing_id, mobile_user_id,
                    listing_title_snapshot, building_name_snapshot, floor_snapshot, room_code_snapshot,
                    area_snapshot, desired_area, intended_use, preferred_visit_time,
                    source, lead_status, status_changed_at, status, created_by, updated_by, remark
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        'MINIPROGRAM', 'NEW', NOW(), 'ACTIVE', ?, ?, ?)
                """,
                JdbcMaps.code("LEAD"),
                contactName,
                mobileUser.get("phone"),
                companyName,
                roomSnapshot,
                listing.get("roomId"),
                listingId,
                mobileUserId,
                listing.get("title"),
                listing.get("buildingName"),
                listing.get("floorName"),
                listing.get("roomNumber"),
                areaSnapshot,
                desiredArea,
                intendedUse,
                preferredVisitTime,
                mobileUserId,
                mobileUserId,
                remark
        );
        return ApiResponse.success();
    }

    @GetMapping({"/mobile/investment/appointments/mine", "/mobile/investment/leads/mine"})
    public ApiResponse<List<Map<String, Object>>> mobileMyLeads(
            @RequestAttribute(value = AuthInterceptor.PRINCIPAL_ATTRIBUTE, required = false) AuthPrincipal principal) {
        if (principal == null) {
            throw BusinessException.unauthorized("请先建立有效微信会话");
        }
        return ApiResponse.success(mobileAppointmentRows(principal.userId(), null));
    }

    @GetMapping("/mobile/investment/appointments/{id}")
    public ApiResponse<Map<String, Object>> mobileAppointmentDetail(
            @PathVariable Long id,
            @RequestAttribute(value = AuthInterceptor.PRINCIPAL_ATTRIBUTE, required = false) AuthPrincipal principal) {
        if (principal == null) {
            throw BusinessException.unauthorized("请先建立有效微信会话");
        }
        List<Map<String, Object>> rows = mobileAppointmentRows(principal.userId(), id);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "预约记录不存在");
        }
        return ApiResponse.success(rows.getFirst());
    }

    private List<Map<String, Object>> mobileAppointmentRows(Long mobileUserId, Long appointmentId) {
        String idCondition = appointmentId == null ? "" : " AND id = ? ";
        List<Object> args = new ArrayList<>();
        args.add(mobileUserId);
        args.add(mobileUserId);
        if (appointmentId != null) {
            args.add(appointmentId);
        }
        return jdbcTemplate.queryForList(
                """
                SELECT id,
                       code,
                       name,
                       phone,
                       company_name AS companyName,
                       desired_area AS desiredArea,
                       listing_id AS listingId,
                       listing_title_snapshot AS listingTitle,
                       building_name_snapshot AS buildingName,
                       floor_snapshot AS floor,
                       room_code_snapshot AS roomCode,
                       area_snapshot AS area,
                       intended_room AS intendedRoom,
                       intended_use AS intendedUse,
                       preferred_visit_time AS preferredVisitTime,
                       lead_status AS status,
                       remark,
                       created_at AS createdAt
                FROM investment_lead
                WHERE deleted = 0
                  AND source = 'MINIPROGRAM'
                  AND (mobile_user_id = ? OR (mobile_user_id IS NULL AND created_by = ?))
                """ + idCondition + """
                ORDER BY created_at DESC, id DESC
                """,
                args.toArray()
        );
    }

    private String contentWhere(
            String keyword,
            String sectionKey,
            String publishStatus,
            List<Object> args
    ) {
        StringBuilder where = new StringBuilder(" WHERE d.deleted = 0 ");
        if (sectionKey != null && !sectionKey.isBlank()) {
            where.append(" AND d.section_key = ? ");
            args.add(normalizeSectionKey(sectionKey));
        }
        if (publishStatus != null && !publishStatus.isBlank()) {
            where.append(" AND d.publish_status = ? ");
            args.add(publishStatus(Map.of("status", publishStatus), "DRAFT"));
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (d.title LIKE ? OR d.subtitle LIKE ? OR d.content LIKE ? OR d.remark LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        return where.toString();
    }

    private String leadWhere(String keyword, String status, String source, List<Object> args) {
        StringBuilder where = new StringBuilder(" WHERE lead_record.deleted = 0 ");
        if (status != null && !status.isBlank()) {
            where.append(" AND lead_record.lead_status = ? ");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
        if (source != null && !source.isBlank()) {
            where.append(" AND lead_record.source = ? ");
            args.add(source.trim().toUpperCase(Locale.ROOT));
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append("""
             AND (lead_record.name LIKE ? OR lead_record.phone LIKE ? OR lead_record.company_name LIKE ?
                  OR lead_record.intended_room LIKE ? OR lead_record.intended_use LIKE ? OR lead_record.remark LIKE ?)
            """);
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        return where.toString();
    }

    private static String workspaceOrderBy(
            Map<String, String> allowedFields,
            String groupBy,
            String sorts,
            String sortField,
            String sortDirection,
            String defaultOrderBy,
            String defaultExpression,
            String stableExpression
    ) {
        if (isBlank(groupBy) && isBlank(sorts) && isBlank(sortField)) {
            return defaultOrderBy;
        }
        return WorkspaceOrderBy.build(
                allowedFields, groupBy, sorts, sortField, sortDirection, defaultExpression, stableExpression
        );
    }

    private CompiledQuery compileCustomQuery(
            String workspaceKey,
            String customFilters,
            String groupBy,
            String sorts,
            Map<String, String> allowedFields,
            String sortField,
            String sortDirection,
            String defaultExpression,
            String stableExpression
    ) {
        if (customFieldQueryService == null) return null;
        NativeOrderPolicy orderPolicy = isBlank(groupBy) && isBlank(sorts)
                ? null
                : NativeOrderPolicy.of(
                        allowedFields,
                        sortField,
                        sortDirection,
                        defaultExpression,
                        stableExpression
                );
        return customFieldQueryService.compile(
                workspaceKey,
                customFilters,
                groupBy,
                sorts,
                orderPolicy
        );
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String listingSelectSql() {
        return """
                SELECT l.id,
                       l.id AS listingId,
                       l.room_id AS roomId,
                       l.display_area_snapshot AS displayAreaSnapshot,
                       l.title,
                       l.subtitle,
                       l.description,
                       l.highlights,
                       l.tags,
                       l.rent_desc AS rentDesc,
                       l.property_fee_desc AS propertyFeeDesc,
                       l.cover_image_url AS coverImageUrl,
                       l.publish_status AS publishStatus,
                       l.is_recommended AS recommended,
                       l.is_reservable AS reservable,
                       l.sort_order AS sortOrder,
                       l.effective_at AS effectiveAt,
                       l.expires_at AS expiresAt,
                       l.updated_at AS updatedAt,
                       b.id AS buildingId,
                       b.name AS buildingName,
                       f.id AS floorId,
                       f.name AS floorName,
                       f.floor_no AS floorNo,
                       r.code AS roomCode,
                       r.room_no AS roomNumber,
                       r.room_no AS roomName,
                       r.area,
                       r.usage_type AS roomType,
                       %s AS status,
                       r.rent_status AS occupancyStatus,
                       r.status AS roomRecordStatus,
                       CASE
                         WHEN l.publish_status = 'PUBLISHED'
                          AND l.is_reservable = 1
                          AND l.status IN ('ACTIVE', 'ENABLED')
                          AND r.status IN ('ACTIVE', 'ENABLED')
                          AND (l.effective_at IS NULL OR l.effective_at <= NOW())
                          AND (l.expires_at IS NULL OR l.expires_at > NOW())
                          AND NOT %s
                         THEN 1 ELSE 0
                       END AS availableForAppointment,
                       COALESCE(r.remark, '') AS roomRemark,
                       (SELECT COUNT(*) FROM investment_room_image img WHERE img.listing_id = l.id AND img.deleted = 0) AS imageCount
                FROM investment_room_listing l
                JOIN building_room r ON r.id = l.room_id
                LEFT JOIN building b ON b.id = r.building_id
                LEFT JOIN building_floor f ON f.id = r.floor_id
                """.formatted(LISTING_RENTAL_STATUS_SQL, ROOM_COMMERCIALLY_OCCUPIED_SQL);
    }

    private String listingWhere(String keyword, String publishStatus, Long buildingId, Long floorId, List<Object> args, boolean publishedMobileOnly) {
        StringBuilder where = new StringBuilder(" WHERE l.deleted = 0 AND r.deleted = 0 ");
        if (publishedMobileOnly) {
            where.append("""
                 AND l.publish_status = 'PUBLISHED'
                 AND l.status IN ('ACTIVE', 'ENABLED')
                 AND r.status IN ('ACTIVE', 'ENABLED')
                 AND (l.effective_at IS NULL OR l.effective_at <= NOW())
                 AND (l.expires_at IS NULL OR l.expires_at > NOW())
                 AND NOT %s
                """.formatted(ROOM_COMMERCIALLY_OCCUPIED_SQL));
        } else if (publishStatus != null && !publishStatus.isBlank()) {
            where.append(" AND l.publish_status = ? ");
            args.add(listingPublishStatus(Map.of("status", publishStatus), "DRAFT"));
        }
        if (buildingId != null) {
            where.append(" AND r.building_id = ? ");
            args.add(buildingId);
        }
        if (floorId != null) {
            where.append(" AND r.floor_id = ? ");
            args.add(floorId);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append("""
             AND (l.title LIKE ? OR l.subtitle LIKE ? OR l.description LIKE ? OR l.tags LIKE ?
                  OR r.room_no LIKE ? OR b.name LIKE ? OR f.name LIKE ?)
            """);
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        return where.toString();
    }

    private List<Map<String, Object>> listingImages(Long listingId) {
        return jdbcTemplate.queryForList(
                """
                SELECT id,
                       listing_id AS listingId,
                       room_id AS roomId,
                       image_url AS imageUrl,
                       image_type AS imageType,
                       sort_order AS sortOrder
                FROM investment_room_image
                WHERE listing_id = ? AND deleted = 0
                ORDER BY image_type = 'COVER' DESC, sort_order ASC, id ASC
                """,
                listingId
        );
    }

    private List<Map<String, Object>> withListingImages(List<Map<String, Object>> rows) {
        List<Long> listingIds = rows.stream()
                .map(row -> longValue(value(row, "listingId", "id")))
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, List<Map<String, Object>>> imagesByListingId = new LinkedHashMap<>();
        if (!listingIds.isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(listingIds.size(), "?"));
            String imageSql = """
                    SELECT id,
                           listing_id AS listingId,
                           room_id AS roomId,
                           image_url AS imageUrl,
                           image_type AS imageType,
                           sort_order AS sortOrder
                    FROM investment_room_image
                    WHERE listing_id IN (%s)
                      AND deleted = 0
                    ORDER BY listing_id ASC, image_type = 'COVER' DESC, sort_order ASC, id ASC
                    """.formatted(placeholders);
            List<Map<String, Object>> images = jdbcTemplate.queryForList(
                    imageSql,
                    listingIds.toArray()
            );
            for (Map<String, Object> image : images) {
                Long listingId = longValue(value(image, "listingId", "listing_id"));
                if (listingId != null) {
                    imagesByListingId.computeIfAbsent(listingId, ignored -> new ArrayList<>()).add(image);
                }
            }
        }
        for (Map<String, Object> row : rows) {
            Long listingId = longValue(value(row, "listingId", "id"));
            attachListingImages(row, imagesByListingId.getOrDefault(listingId, List.of()));
        }
        return rows;
    }

    private void attachListingImages(Map<String, Object> row) {
        Long listingId = longValue(value(row, "listingId", "id"));
        attachListingImages(row, listingId == null ? List.of() : listingImages(listingId));
    }

    private void attachListingImages(Map<String, Object> row, List<Map<String, Object>> listingImages) {
        List<String> imageUrls = new ArrayList<>();
        for (Map<String, Object> image : listingImages) {
            String imageUrl = str(value(image, "imageUrl", "image_url"));
            if (!imageUrl.isBlank()) {
                imageUrls.add(imageUrl);
            }
        }
        String coverImageUrl = str(value(row, "coverImageUrl", "cover_image_url"));
        if (imageUrls.isEmpty() && !coverImageUrl.isBlank()) {
            imageUrls.add(coverImageUrl);
        }
        if (coverImageUrl.isBlank() && !imageUrls.isEmpty()) {
            row.put("coverImageUrl", imageUrls.getFirst());
        }
        row.put("images", listingImages);
        row.put("imageUrls", imageUrls);
    }

    private List<Map<String, Object>> publicListingRows(List<Map<String, Object>> rows) {
        return rows.stream().map(this::publicListing).toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> publicListing(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>(source);
        for (String internalKey : List.of(
                "roomId", "buildingId", "floorId", "roomCode", "roomRemark",
                "roomRecordStatus", "updatedAt", "displayAreaSnapshot")) {
            result.remove(internalKey);
        }
        Object imagesValue = result.get("images");
        if (imagesValue instanceof List<?> images) {
            List<Map<String, Object>> publicImages = new ArrayList<>();
            for (Object imageValue : images) {
                if (!(imageValue instanceof Map<?, ?> image)) continue;
                Map<String, Object> publicImage = new LinkedHashMap<>();
                publicImage.put("imageUrl", image.get("imageUrl"));
                publicImage.put("imageType", image.get("imageType"));
                publicImage.put("sortOrder", image.get("sortOrder"));
                publicImages.add(publicImage);
            }
            result.put("images", publicImages);
        }
        return result;
    }

    private Map<String, Object> ensureListingExists(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, room_id, title, cover_image_url, publish_status FROM investment_room_listing WHERE id = ? AND deleted = 0",
                id
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "Investment listing not found");
        }
        return rows.getFirst();
    }

    private Map<String, Object> appointmentListing(Long listingId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                listingSelectSql() + " WHERE l.id = ? AND l.deleted = 0",
                listingId
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "招商房源不存在");
        }
        Map<String, Object> listing = rows.getFirst();
        if (!truthy(listing.get("availableForAppointment"))) {
            throw new BusinessException(409, "该房源已下架、已出租或当前不可预约");
        }
        return listing;
    }

    private Map<String, Object> verifiedMobileUser(Long mobileUserId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id,
                       phone,
                       COALESCE(NULLIF(real_name, ''), NULLIF(nickname, ''), '') AS profileName
                FROM mobile_user
                WHERE id = ?
                  AND status IN ('ACTIVE', 'ENABLED')
                  AND deleted = 0
                """,
                mobileUserId
        );
        if (rows.isEmpty()) {
            throw BusinessException.unauthorized("微信技术会话已失效");
        }
        String phone = str(rows.getFirst().get("phone"));
        if (phone.isBlank()) {
            throw new BusinessException(409, "预约看房前请先完成微信手机号授权");
        }
        return rows.getFirst();
    }

    private void ensureAppointmentRateLimit(Long mobileUserId, Long listingId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM investment_lead
                WHERE mobile_user_id = ?
                  AND listing_id = ?
                  AND source = 'MINIPROGRAM'
                  AND created_at >= DATE_SUB(NOW(), INTERVAL 60 SECOND)
                  AND deleted = 0
                """,
                Integer.class,
                mobileUserId,
                listingId
        );
        if (count != null && count > 0) {
            throw new BusinessException(429, "预约提交过于频繁，请稍后再试");
        }
    }

    private Map<String, Object> ensureLeadExists(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, lead_status FROM investment_lead WHERE id = ? AND deleted = 0",
                id
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "Investment lead not found");
        }
        return rows.getFirst();
    }

    private List<Map<String, Object>> followRecords(Long leadId) {
        return jdbcTemplate.queryForList(
                """
                SELECT id,
                       follower_id AS followerId,
                       follow_type AS followType,
                       content,
                       next_follow_at AS nextFollowAt,
                       created_at AS createdAt
                FROM investment_follow_record
                WHERE lead_id = ? AND deleted = 0
                ORDER BY created_at DESC, id DESC
                """,
                leadId
        );
    }

    private void transitionLead(Long id, String targetStatus, Long operatorId, String remark) {
        Map<String, Object> lead = ensureLeadExists(id);
        String currentStatus = normalizeExistingLeadStatus(lead.get("lead_status"));
        if (currentStatus.equals(targetStatus)) {
            return;
        }
        if (TERMINAL_LEAD_STATUSES.contains(currentStatus)) {
            throw new BusinessException(409, "已转化或无效线索不能回退");
        }
        if (!allowedLeadTransitions(currentStatus).contains(targetStatus)) {
            throw new BusinessException(409, "当前线索状态不允许该操作");
        }
        ensureUpdated(jdbcTemplate.update(
                """
                UPDATE investment_lead
                SET lead_status = ?, status_changed_at = NOW(), status_changed_by = ?, updated_by = ?
                WHERE id = ? AND deleted = 0 AND lead_status = ?
                """,
                targetStatus,
                operatorId,
                operatorId,
                id,
                lead.get("lead_status")
        ), "Investment lead not found");
        jdbcTemplate.update(
                """
                INSERT INTO investment_lead_status_history (
                    lead_id, from_status, to_status, operator_id, changed_at,
                    remark, status, created_by, updated_by
                )
                VALUES (?, ?, ?, ?, NOW(), ?, 'ENABLED', ?, ?)
                """,
                id,
                currentStatus,
                targetStatus,
                operatorId,
                remark,
                operatorId,
                operatorId
        );
    }

    private static Set<String> allowedLeadTransitions(String currentStatus) {
        return switch (currentStatus) {
            case "NEW" -> Set.of("FOLLOWING", "VIEWING_SCHEDULED", "INVALID");
            case "FOLLOWING" -> Set.of("VIEWING_SCHEDULED", "VIEWED", "CONVERTED", "INVALID");
            case "VIEWING_SCHEDULED" -> Set.of("FOLLOWING", "VIEWED", "INVALID");
            case "VIEWED" -> Set.of("FOLLOWING", "CONVERTED", "INVALID");
            default -> Set.of();
        };
    }

    private static String normalizeExistingLeadStatus(Object value) {
        String status = str(value).toUpperCase(Locale.ROOT);
        return switch (status) {
            case "CONTACTED", "NEGOTIATING" -> "FOLLOWING";
            case "CONVERTED_TENANT", "CONVERTED_CONTRACT", "CLOSED" -> "CONVERTED";
            default -> LEAD_STATUSES.contains(status) ? status : "NEW";
        };
    }

    private static String normalizedLeadStatus(Map<String, Object> body) {
        String requested = JdbcMaps.strOr(body, "FOLLOWING", "status").trim().toUpperCase(Locale.ROOT);
        String normalized = switch (requested) {
            case "CONTACTED", "NEGOTIATING" -> "FOLLOWING";
            case "CONVERTED_TENANT", "CONVERTED_CONTRACT", "CLOSED" -> "CONVERTED";
            default -> requested;
        };
        if (!LEAD_STATUSES.contains(normalized)) {
            throw new BusinessException(400, "不支持的招商线索状态");
        }
        return normalized;
    }

    private static String normalizedFollowType(Map<String, Object> body) {
        String value = JdbcMaps.strOr(body, "OTHER", "followType", "type").trim().toUpperCase(Locale.ROOT);
        return Set.of("PHONE", "WECHAT", "OFFLINE", "VISIT", "OTHER").contains(value) ? value : "OTHER";
    }

    private static Long operatorId(AuthPrincipal principal) {
        return principal == null || principal.userId() == null ? 0L : principal.userId();
    }

    private void ensureListingPublishable(Long listingId, Long roomId, String title, String coverImageUrl) {
        if (roomId == null) {
            throw new BusinessException(400, "招商房源必须关联有效房间");
        }
        if (title == null || title.isBlank()) {
            throw new BusinessException(400, "上架前请填写展示标题");
        }

        List<Map<String, Object>> rooms = jdbcTemplate.queryForList(
                """
                SELECT rent_status AS status,
                       status AS record_status,
                       EXISTS(
                           SELECT 1
                           FROM lease_room active_lease_room
                           WHERE active_lease_room.room_id = building_room.id
                             AND active_lease_room.status = 'ACTIVE'
                             AND active_lease_room.occupancy_end_at IS NULL
                             AND active_lease_room.deleted = 0
                       ) AS occupied_by_lease,
                       EXISTS(
                           SELECT 1
                           FROM contract_room contract_room_record
                           JOIN contract contract_record ON contract_record.id = contract_room_record.contract_id
                           WHERE contract_room_record.room_id = building_room.id
                             AND contract_room_record.deleted = 0
                             AND contract_record.deleted = 0
                             AND contract_record.status = 'ACTIVE'
                       ) AS occupied_by_contract
                FROM building_room
                WHERE id = ? AND deleted = 0
                """,
                roomId
        );
        if (rooms.isEmpty()) {
            throw new BusinessException(404, "关联房间不存在或已删除");
        }
        String roomStatus = str(rooms.getFirst().get("status")).toUpperCase(Locale.ROOT);
        String recordStatus = str(rooms.getFirst().get("record_status")).toUpperCase(Locale.ROOT);
        if (!List.of("VACANT", "AVAILABLE").contains(roomStatus)
                || (!recordStatus.isBlank() && !List.of("ACTIVE", "ENABLED").contains(recordStatus))
                || truthy(rooms.getFirst().get("occupied_by_lease"))
                || truthy(rooms.getFirst().get("occupied_by_contract"))) {
            throw new BusinessException(409, "关联房间当前状态不允许公开招商");
        }

        Integer imageCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM investment_room_image WHERE listing_id = ? AND deleted = 0",
                Integer.class,
                listingId
        );
        if ((coverImageUrl == null || coverImageUrl.isBlank()) && (imageCount == null || imageCount == 0)) {
            throw new BusinessException(400, "上架前请至少上传一张房源图片");
        }

        Integer duplicateCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM investment_room_listing WHERE room_id = ? AND publish_status = 'PUBLISHED' AND deleted = 0 AND id <> ?",
                Integer.class,
                roomId,
                listingId
        );
        if (duplicateCount != null && duplicateCount > 0) {
            throw new BusinessException(409, "该房间已有公开招商房源，请先下架原房源");
        }
    }

    private Map<String, Object> ensureImageExists(Long imageId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, listing_id, image_url FROM investment_room_image WHERE id = ? AND deleted = 0",
                imageId
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "Image not found");
        }
        return rows.getFirst();
    }

    private void ensureRoomExists(Long roomId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM building_room WHERE id = ? AND deleted = 0",
                Integer.class,
                roomId
        );
        if (count == null || count == 0) {
            throw new BusinessException(404, "Room not found");
        }
    }

    private BigDecimal roomArea(Long roomId) {
        BigDecimal area = jdbcTemplate.queryForObject(
                "SELECT area FROM building_room WHERE id = ? AND deleted = 0",
                BigDecimal.class,
                roomId
        );
        return area == null ? BigDecimal.ZERO : area;
    }

    private String saveListingImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择需要上传的图片");
        }
        if (file.getSize() > MAX_LISTING_IMAGE_SIZE) {
            throw new BusinessException(400, "单张图片不能超过20MB");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String lower = original.toLowerCase(Locale.ROOT);
        String ext = lower.endsWith(".jpeg") ? ".jpeg"
                : lower.endsWith(".jpg") ? ".jpg"
                : lower.endsWith(".png") ? ".png"
                : lower.endsWith(".webp") ? ".webp"
                : "";
        if (ext.isBlank()) {
            throw new BusinessException(400, "仅支持 JPG、JPEG、PNG 和 WebP 图片");
        }
        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().trim().toLowerCase(Locale.ROOT);
        boolean validMime = switch (ext) {
            case ".jpg", ".jpeg" -> Set.of("image/jpeg", "image/jpg").contains(contentType);
            case ".png" -> "image/png".equals(contentType);
            case ".webp" -> "image/webp".equals(contentType);
            default -> false;
        };
        if (!validMime) {
            throw new BusinessException(400, "文件内容与允许的图片类型不匹配");
        }
        try {
            Path dir = Paths.get(uploadDir).toAbsolutePath().normalize().resolve("public").resolve("investment").resolve("rooms");
            Files.createDirectories(dir);
            String filename = UUID.randomUUID().toString().replace("-", "") + ext;
            Path target = dir.resolve(filename).normalize();
            file.transferTo(target);
            return "/api/files/public/investment/rooms/" + filename;
        } catch (Exception e) {
            throw new BusinessException(500, "图片上传失败");
        }
    }

    private void setImageAsCover(Long imageId) {
        Map<String, Object> image = ensureImageExists(imageId);
        Long listingId = ((Number) image.get("listing_id")).longValue();
        jdbcTemplate.update("UPDATE investment_room_image SET image_type = 'GALLERY', updated_by = ? WHERE listing_id = ? AND deleted = 0", 0L, listingId);
        jdbcTemplate.update("UPDATE investment_room_image SET image_type = 'COVER', updated_by = ? WHERE id = ? AND deleted = 0", 0L, imageId);
        jdbcTemplate.update("UPDATE investment_room_listing SET cover_image_url = ?, updated_by = ? WHERE id = ? AND deleted = 0", image.get("image_url"), 0L, listingId);
    }

    private void clearCoverIfNeeded(Map<String, Object> image) {
        Long listingId = ((Number) image.get("listing_id")).longValue();
        List<Map<String, Object>> listings = jdbcTemplate.queryForList(
                "SELECT cover_image_url FROM investment_room_listing WHERE id = ? AND deleted = 0",
                listingId
        );
        if (!listings.isEmpty() && str(listings.getFirst().get("cover_image_url")).equals(str(image.get("image_url")))) {
            jdbcTemplate.update("UPDATE investment_room_listing SET cover_image_url = NULL, updated_by = ? WHERE id = ? AND deleted = 0", 0L, listingId);
        }
    }

    private void ensureUpdated(int updated, String message) {
        if (updated == 0) {
            throw new BusinessException(404, message);
        }
    }

    private Map<String, Object> groupedContent(boolean publishedOnly) {
        String filter = publishedOnly
                ? """
                   AND publish_status = 'PUBLISHED'
                   AND public_visible = 1
                   AND status IN ('ACTIVE', 'ENABLED')
                   AND (effective_at IS NULL OR effective_at <= NOW())
                   AND (expires_at IS NULL OR expires_at > NOW())
                   AND section_key IN ('hero', 'highlight', 'policy', 'introduction', 'location', 'contact')
                  """
                : "";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT section_key AS sectionKey,
                       title,
                       COALESCE(subtitle, '') AS subtitle,
                       content,
                       COALESCE(image_url, '') AS imageUrl,
                       effective_at AS effectiveAt,
                       expires_at AS expiresAt,
                       sort_order AS sortOrder
                FROM investment_display
                WHERE deleted = 0
                """ + filter + """
                ORDER BY section_key ASC, sort_order ASC, id ASC
                """
        );
        return groupRows(rows);
    }

    private static Map<String, Object> groupRows(List<Map<String, Object>> rows) {
        Map<String, Object> grouped = new LinkedHashMap<>();
        for (String key : SECTION_KEYS) {
            grouped.put(key, new ArrayList<Map<String, Object>>());
        }

        for (Map<String, Object> row : rows) {
            String key = normalizeSectionKey(value(row, "sectionKey", "section_key"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sectionRows = (List<Map<String, Object>>) grouped.get(key);
            if (sectionRows == null) {
                continue;
            }
            sectionRows.add(Map.of(
                    "sectionKey", key,
                    "sectionName", SECTION_NAMES.getOrDefault(key, key),
                    "title", str(value(row, "title")),
                    "subtitle", str(value(row, "subtitle")),
                    "content", str(value(row, "content")),
                    "imageUrl", str(value(row, "imageUrl", "image_url")),
                    "sortOrder", row.getOrDefault("sortOrder", row.getOrDefault("sort_order", 0))
            ));
        }

        return grouped;
    }

    @SuppressWarnings("unchecked")
    private static void appendLegacyMiniProgramFields(Map<String, Object> grouped) {
        List<Map<String, Object>> hero = (List<Map<String, Object>>) grouped.getOrDefault("hero", List.of());
        List<Map<String, Object>> highlights = (List<Map<String, Object>>) grouped.getOrDefault("highlight", List.of());
        List<Map<String, Object>> policies = (List<Map<String, Object>>) grouped.getOrDefault("policy", List.of());
        List<Map<String, Object>> introductions = (List<Map<String, Object>>) grouped.getOrDefault("introduction", List.of());
        List<Map<String, Object>> locations = (List<Map<String, Object>>) grouped.getOrDefault("location", List.of());
        List<Map<String, Object>> contacts = (List<Map<String, Object>>) grouped.getOrDefault("contact", List.of());
        if (!hero.isEmpty()) {
            Map<String, Object> main = hero.getFirst();
            grouped.put("title", str(main.get("title")));
            grouped.put("subtitle", str(main.get("subtitle")).isBlank() ? str(main.get("content")) : str(main.get("subtitle")));
            grouped.put("heroImageUrl", str(main.get("imageUrl")));
        }
        if (!introductions.isEmpty()) {
            Map<String, Object> introduction = introductions.getFirst();
            grouped.put(
                    "description",
                    str(introduction.get("content")).isBlank()
                            ? str(introduction.get("title"))
                            : str(introduction.get("content"))
            );
        }
        if (!locations.isEmpty()) {
            grouped.put("address", str(locations.getFirst().get("content")));
        }
        if (!contacts.isEmpty()) {
            Map<String, Object> contact = contacts.getFirst();
            String phone = str(contact.get("content"));
            grouped.put("phone", phone.isBlank() ? str(contact.get("subtitle")) : phone);
        }
        grouped.put("highlights", highlights.stream()
                .map(item -> str(item.get("content")).isBlank() ? str(item.get("title")) : str(item.get("content")))
                .toList());
        grouped.put("policies", policies.stream()
                .map(item -> str(item.get("content")).isBlank() ? str(item.get("title")) : str(item.get("content")))
                .toList());
    }

    private static String sectionKey(Map<String, Object> body) {
        return normalizeSectionKey(JdbcMaps.str(body, "sectionKey", "contentType", "type", "sectionName"));
    }

    private static String normalizeSectionKey(Object value) {
        String raw = value == null ? "" : String.valueOf(value).trim();
        String normalized = raw.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "hero", "overview", "main", "top", "主标题", "顶部主视觉", "顶部主视觉区域" -> "hero";
            case "highlight", "highlights", "亮点", "园区亮点", "hightlight" -> "highlight";
            case "policy", "policies", "招商政策", "政策" -> "policy";
            case "introduction", "intro", "园区介绍", "介绍" -> "introduction";
            case "location", "address", "园区地址", "地址", "位置" -> "location";
            case "contact", "联系方式", "联系电话", "电话" -> "contact";
            default -> SECTION_KEYS.contains(normalized) ? normalized : "highlight";
        };
    }

    private static String publishStatus(Map<String, Object> body, String fallback) {
        String status = JdbcMaps.strOr(body, fallback, "publishStatus", "status");
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PUBLISHED", "PUBLISH", "已发布", "发布" -> "PUBLISHED";
            case "DISABLED", "DISABLE", "INACTIVE", "停用", "禁用" -> "DISABLED";
            default -> "DRAFT";
        };
    }

    private static Object value(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
        }
        return null;
    }

    private static Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            String text = str(value);
            return text.isBlank() ? null : Long.parseLong(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String listingPublishStatus(Map<String, Object> body, String fallback) {
        String status = JdbcMaps.strOr(body, fallback, "publishStatus", "status");
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PUBLISHED", "PUBLISH" -> "PUBLISHED";
            case "OFFLINE", "DISABLED", "DISABLE", "INACTIVE" -> "OFFLINE";
            default -> "DRAFT";
        };
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static void validateEffectiveWindow(Map<String, Object> body) {
        String effectiveAt = JdbcMaps.datetime(body, null, "effectiveAt");
        String expiresAt = JdbcMaps.datetime(body, null, "expiresAt");
        if (effectiveAt != null && expiresAt != null && expiresAt.compareTo(effectiveAt) <= 0) {
            throw new BusinessException(400, "失效时间必须晚于生效时间");
        }
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = str(value).trim();
        return "1".equals(text) || "TRUE".equalsIgnoreCase(text) || "YES".equalsIgnoreCase(text);
    }

    private static BigDecimal decimalValue(Object value, BigDecimal fallback) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            String text = str(value).trim();
            return text.isBlank() ? fallback : new BigDecimal(text);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String validatedContactName(Map<String, Object> body) {
        String value = JdbcMaps.requiredStr(body, "请输入正确的联系人姓名", "name", "contactName");
        if (!CONTACT_NAME_PATTERN.matcher(value).matches() || hasUnsafeText(value)) {
            throw new BusinessException(400, "请输入正确的联系人姓名");
        }
        return value;
    }

    private static String validatedPhone(Map<String, Object> body) {
        String value = JdbcMaps.requiredStr(body, "联系电话格式不正确", "phone", "contactPhone");
        if (!CONTACT_PHONE_PATTERN.matcher(value).matches()) {
            throw new BusinessException(400, "联系电话格式不正确");
        }
        return value;
    }

    private static String validatedCompanyName(Map<String, Object> body) {
        String value = JdbcMaps.requiredStr(body, "请输入正确的公司名称", "companyName", "company");
        if (!COMPANY_NAME_PATTERN.matcher(value).matches() || hasUnsafeText(value)) {
            throw new BusinessException(400, "请输入正确的公司名称");
        }
        return value;
    }

    private static String validatedIntendedRoom(Map<String, Object> body) {
        String value = JdbcMaps.str(body, "intendedRoom", "roomNumber");
        if (value == null) {
            return null;
        }
        if (!INTENDED_ROOM_PATTERN.matcher(value).matches() || hasUnsafeText(value)) {
            throw new BusinessException(400, "请输入正确的意向房源信息");
        }
        return value;
    }

    private static BigDecimal validatedDesiredArea(Map<String, Object> body) {
        String value = JdbcMaps.str(body, "desiredArea");
        if (value == null) {
            return null;
        }
        if (!value.matches("^\\d+(\\.\\d{1,2})?$")) {
            throw new BusinessException(400, "请输入正确的意向面积，只能填写数字");
        }
        BigDecimal area = new BigDecimal(value);
        if (area.compareTo(BigDecimal.ONE) < 0 || area.compareTo(new BigDecimal("100000")) > 0) {
            throw new BusinessException(400, "请输入正确的意向面积，只能填写数字");
        }
        return area;
    }

    private static String validatedIntendedUse(Map<String, Object> body) {
        String value = JdbcMaps.str(body, "intendedUse");
        if (value == null) {
            return null;
        }
        if (!INTENDED_USE_PATTERN.matcher(value).matches() || hasUnsafeText(value)) {
            throw new BusinessException(400, "请输入正确的用途说明");
        }
        return value;
    }

    private static String validatedRemark(Map<String, Object> body) {
        String value = JdbcMaps.str(body, "remark");
        if (value == null) {
            return null;
        }
        if (value.length() > 200 || hasUnsafeText(value)) {
            throw new BusinessException(400, "备注最多 200 个字符，不能包含特殊脚本内容");
        }
        return value;
    }

    private static boolean hasUnsafeText(String value) {
        return value.indexOf('<') >= 0
                || value.indexOf('>') >= 0
                || value.indexOf(';') >= 0
                || UNSAFE_TEXT_PATTERN.matcher(value).find();
    }
}
