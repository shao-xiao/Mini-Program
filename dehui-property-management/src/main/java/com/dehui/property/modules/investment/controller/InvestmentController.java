package com.dehui.property.modules.investment.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcPagination;
import com.dehui.property.common.JdbcMaps;
import com.dehui.property.common.PageResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InvestmentController {

    private static final List<String> SECTION_KEYS = List.of(
            "hero",
            "highlight",
            "policy",
            "introduction",
            "location",
            "contact",
            "notice"
    );

    private static final Map<String, String> SECTION_NAMES = Map.of(
            "hero", "顶部主视觉",
            "highlight", "园区亮点",
            "policy", "招商政策",
            "introduction", "园区介绍",
            "location", "园区地址",
            "contact", "联系方式",
            "notice", "招商公告"
    );

    private final JdbcTemplate jdbcTemplate;

    @GetMapping({"/investment/displays", "/investment/contents"})
    public ApiResponse<PageResponse<Map<String, Object>>> displays(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(JdbcPagination.query(
                jdbcTemplate,
                """
                SELECT id,
                       code,
                       section_key AS sectionKey,
                       CASE section_key
                         WHEN 'hero' THEN '顶部主视觉'
                         WHEN 'highlight' THEN '园区亮点'
                         WHEN 'policy' THEN '招商政策'
                         WHEN 'introduction' THEN '园区介绍'
                         WHEN 'location' THEN '园区地址'
                         WHEN 'contact' THEN '联系方式'
                         WHEN 'notice' THEN '招商公告'
                         ELSE section_key
                       END AS sectionName,
                       title,
                       subtitle,
                       content,
                       image_url AS imageUrl,
                       publish_status AS publishStatus,
                       publish_status AS status,
                       sort_order AS sortOrder,
                       status AS recordStatus,
                       remark,
                       updated_at AS updatedAt
                FROM investment_display
                WHERE deleted = 0
                ORDER BY section_key ASC, sort_order ASC, id DESC
                """,
                "SELECT COUNT(*) FROM investment_display WHERE deleted = 0",
                List.of(),
                page,
                pageSize
        ));
    }

    @PostMapping("/investment/contents")
    public ApiResponse<Void> createContent(@RequestBody Map<String, Object> body) {
        jdbcTemplate.update(
                """
                INSERT INTO investment_display (code, section_key, title, subtitle, content, image_url, publish_status, sort_order, status, created_by, updated_by, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                """,
                JdbcMaps.code("INV"),
                sectionKey(body),
                JdbcMaps.requiredStr(body, "Title is required", "title"),
                JdbcMaps.strOr(body, "", "subtitle"),
                JdbcMaps.strOr(body, "", "content"),
                JdbcMaps.strOr(body, "", "imageUrl", "image"),
                publishStatus(body, "DRAFT"),
                JdbcMaps.intVal(body, 0, "sortOrder"),
                0L,
                0L,
                JdbcMaps.str(body, "remark", "summary")
        );
        return ApiResponse.success();
    }

    @PutMapping("/investment/contents/{id}")
    public ApiResponse<Void> updateContent(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        int updated = jdbcTemplate.update(
                """
                UPDATE investment_display
                SET section_key = ?, title = ?, subtitle = ?, content = ?, image_url = ?, publish_status = ?, sort_order = ?, updated_by = ?, remark = ?
                WHERE id = ? AND deleted = 0
                """,
                sectionKey(body),
                JdbcMaps.requiredStr(body, "Title is required", "title"),
                JdbcMaps.strOr(body, "", "subtitle"),
                JdbcMaps.strOr(body, "", "content"),
                JdbcMaps.strOr(body, "", "imageUrl", "image"),
                publishStatus(body, "DRAFT"),
                JdbcMaps.intVal(body, 0, "sortOrder"),
                0L,
                JdbcMaps.str(body, "remark", "summary"),
                id
        );
        ensureUpdated(updated, "Investment content not found");
        return ApiResponse.success();
    }

    @PostMapping("/investment/contents/{id}/publish")
    public ApiResponse<Void> publishContent(@PathVariable Long id) {
        ensureUpdated(jdbcTemplate.update("UPDATE investment_display SET publish_status = 'PUBLISHED', updated_by = ? WHERE id = ? AND deleted = 0", 0L, id), "Investment content not found");
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
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(JdbcPagination.query(
                jdbcTemplate,
                """
                SELECT id, code, name, phone, company_name AS companyName, desired_area AS desiredArea,
                       intended_use AS intendedUse, source, lead_status AS status, remark
                FROM investment_lead
                WHERE deleted = 0
                ORDER BY updated_at DESC, id DESC
                """,
                "SELECT COUNT(*) FROM investment_lead WHERE deleted = 0",
                List.of(),
                page,
                pageSize
        ));
    }

    @PatchMapping("/investment/leads/{id}/status")
    public ApiResponse<Void> updateLeadStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ensureUpdated(jdbcTemplate.update("UPDATE investment_lead SET lead_status = ?, updated_by = ? WHERE id = ? AND deleted = 0",
                JdbcMaps.strOr(body, "FOLLOWING", "status"), 0L, id), "Investment lead not found");
        return ApiResponse.success();
    }

    @PostMapping("/investment/leads/{id}/convert-tenant")
    public ApiResponse<Void> convertTenant(@PathVariable Long id) {
        Map<String, Object> lead = jdbcTemplate.queryForMap("SELECT * FROM investment_lead WHERE id = ? AND deleted = 0", id);
        jdbcTemplate.update(
                "INSERT INTO tenant (code, name, contact_phone, status, created_by, updated_by, remark) VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?)",
                JdbcMaps.code("TEN"), lead.get("company_name") == null ? lead.get("name") : lead.get("company_name"), lead.get("phone"), 0L, 0L, "from lead " + id
        );
        updateLeadStatus(id, Map.of("status", "CONVERTED_TENANT"));
        return ApiResponse.success();
    }

    @PostMapping("/investment/leads/{id}/convert-contract")
    public ApiResponse<Void> convertContract(@PathVariable Long id) {
        updateLeadStatus(id, Map.of("status", "CONVERTED_CONTRACT"));
        return ApiResponse.success();
    }

    @GetMapping({"/mobile/investment/overview", "/mobile/investment-content", "/miniapp/investment-content"})
    public ApiResponse<Map<String, Object>> mobileOverview() {
        return ApiResponse.success(groupedContent(true));
    }

    @PostMapping("/mobile/investment/leads")
    public ApiResponse<Void> mobileLead(@RequestBody Map<String, Object> body) {
        jdbcTemplate.update(
                """
                INSERT INTO investment_lead (code, name, phone, company_name, desired_area, intended_use, source, lead_status, status, created_by, updated_by, remark)
                VALUES (?, ?, ?, ?, ?, ?, 'MINIPROGRAM', 'NEW', 'ACTIVE', ?, ?, ?)
                """,
                JdbcMaps.code("LEAD"),
                JdbcMaps.requiredStr(body, "Name is required", "name"),
                JdbcMaps.requiredStr(body, "Phone is required", "phone"),
                JdbcMaps.str(body, "companyName", "company"),
                JdbcMaps.decimal(body, BigDecimal.ZERO, "desiredArea"),
                JdbcMaps.str(body, "intendedUse"),
                0L,
                0L,
                JdbcMaps.str(body, "remark")
        );
        return ApiResponse.success();
    }

    private void ensureUpdated(int updated, String message) {
        if (updated == 0) {
            throw new BusinessException(404, message);
        }
    }

    private Map<String, Object> groupedContent(boolean publishedOnly) {
        String filter = publishedOnly ? " AND publish_status = 'PUBLISHED'" : "";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT section_key AS sectionKey,
                       title,
                       COALESCE(subtitle, '') AS subtitle,
                       content,
                       COALESCE(image_url, '') AS imageUrl,
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
            case "notice", "招商公告", "公告", "通知" -> "notice";
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

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
