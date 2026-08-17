package com.dehui.property.modules.notice.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcMaps;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
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
public class NoticeController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping({"/notices", "/announcements"})
    public ApiResponse<List<Map<String, Object>>> notices() {
        return ApiResponse.success(jdbcTemplate.queryForList(
                """
                SELECT id,
                       code,
                       title,
                       summary,
                       content,
                       cover_image_url AS coverImageUrl,
                       notice_type AS type,
                       notice_type AS noticeType,
                       publish_status AS status,
                       publish_status AS publishStatus,
                       published_at AS publishTime,
                       published_at AS publishedAt,
                       created_at AS createdTime,
                       updated_at AS updatedTime,
                       show_on_home AS showOnHome,
                       carousel_sort AS carouselSort,
                       effective_at AS effectiveAt,
                       expires_at AS expiresAt,
                       visibility_scope AS visibilityScope,
                       remark
                FROM notice
                WHERE deleted = 0
                ORDER BY updated_at DESC, id DESC
                """
        ));
    }

    @GetMapping("/mobile/announcements")
    public ApiResponse<List<Map<String, Object>>> mobileNotices(
            @RequestParam(name = "homeCarousel", required = false, defaultValue = "false") boolean homeCarousel
    ) {
        String homeCarouselCondition = homeCarousel ? "\n                  AND show_on_home = 1\n" : "";
        String orderBy = homeCarousel
                ? "carousel_sort ASC, COALESCE(published_at, updated_at) DESC, id DESC"
                : "COALESCE(published_at, updated_at) DESC, id DESC";
        return ApiResponse.success(jdbcTemplate.queryForList(
                """
                SELECT id,
                       code,
                       title,
                       summary,
                       content,
                       cover_image_url AS coverImageUrl,
                       notice_type AS type,
                       notice_type AS noticeType,
                       published_at AS publishTime,
                       published_at AS publishedAt,
                       created_at AS createdTime,
                       show_on_home AS showOnHome,
                       carousel_sort AS carouselSort,
                       effective_at AS effectiveAt,
                       expires_at AS expiresAt,
                       status,
                       remark
                FROM notice
                WHERE deleted = 0
                  AND status IN ('ACTIVE', 'ENABLED')
                  AND publish_status IN ('PUBLISHED', 'ACTIVE')
                  AND visibility_scope = 'PUBLIC'
                  AND (effective_at IS NULL OR effective_at <= NOW())
                  AND (expires_at IS NULL OR expires_at > NOW())
                """ + homeCarouselCondition + " ORDER BY " + orderBy
        ));
    }

    @GetMapping("/mobile/announcements/carousel")
    public ApiResponse<List<Map<String, Object>>> mobileCarousel() {
        return mobileNotices(true);
    }

    @GetMapping("/mobile/announcements/{id}")
    public ApiResponse<Map<String, Object>> mobileDetail(@PathVariable Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id,
                       code,
                       title,
                       summary,
                       content,
                       cover_image_url AS coverImageUrl,
                       notice_type AS type,
                       notice_type AS noticeType,
                       published_at AS publishTime,
                       published_at AS publishedAt,
                       created_at AS createdTime,
                       show_on_home AS showOnHome,
                       carousel_sort AS carouselSort,
                       effective_at AS effectiveAt,
                       expires_at AS expiresAt,
                       status,
                       remark
                FROM notice
                WHERE id = ?
                  AND deleted = 0
                  AND status IN ('ACTIVE', 'ENABLED')
                  AND publish_status IN ('PUBLISHED', 'ACTIVE')
                  AND visibility_scope = 'PUBLIC'
                  AND (effective_at IS NULL OR effective_at <= NOW())
                  AND (expires_at IS NULL OR expires_at > NOW())
                LIMIT 1
                """,
                id
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "Notice not found");
        }
        return ApiResponse.success(rows.getFirst());
    }

    @PostMapping("/announcements")
    public ApiResponse<Void> create(@RequestBody Map<String, Object> body) {
        String publishStatus = normalizedPublishStatus(body);
        validateWindow(body);
        jdbcTemplate.update(
                """
                INSERT INTO notice (
                    code, title, summary, content, cover_image_url, notice_type,
                    publish_status, show_on_home, carousel_sort, effective_at, expires_at,
                    visibility_scope, status, created_by, updated_by, remark
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PUBLIC', 'ACTIVE', ?, ?, ?)
                """,
                JdbcMaps.code("NOT"),
                JdbcMaps.requiredStr(body, "Title is required", "title"),
                JdbcMaps.str(body, "summary"),
                JdbcMaps.strOr(body, "", "content"),
                JdbcMaps.str(body, "coverImageUrl"),
                normalizedNoticeType(body),
                publishStatus,
                homeCarouselFlag(body, publishStatus),
                JdbcMaps.intVal(body, 0, "carouselSort"),
                JdbcMaps.datetime(body, null, "effectiveAt"),
                JdbcMaps.datetime(body, null, "expiresAt"),
                0L,
                0L,
                JdbcMaps.str(body, "remark")
        );
        return ApiResponse.success();
    }

    @PutMapping("/announcements/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String publishStatus = normalizedPublishStatus(body);
        validateWindow(body);
        int updated = jdbcTemplate.update(
                """
                UPDATE notice
                SET title = ?, summary = ?, content = ?, cover_image_url = ?,
                    notice_type = ?, publish_status = ?, show_on_home = ?,
                    carousel_sort = ?, effective_at = ?, expires_at = ?,
                    visibility_scope = 'PUBLIC', updated_by = ?, remark = ?
                WHERE id = ? AND deleted = 0
                """,
                JdbcMaps.requiredStr(body, "Title is required", "title"),
                JdbcMaps.str(body, "summary"),
                JdbcMaps.strOr(body, "", "content"),
                JdbcMaps.str(body, "coverImageUrl"),
                normalizedNoticeType(body),
                publishStatus,
                homeCarouselFlag(body, publishStatus),
                JdbcMaps.intVal(body, 0, "carouselSort"),
                JdbcMaps.datetime(body, null, "effectiveAt"),
                JdbcMaps.datetime(body, null, "expiresAt"),
                0L,
                JdbcMaps.str(body, "remark"),
                id
        );
        ensureUpdated(updated);
        return ApiResponse.success();
    }

    @PostMapping("/announcements/{id}/publish")
    public ApiResponse<Void> publish(@PathVariable Long id) {
        int updated = jdbcTemplate.update(
                "UPDATE notice SET publish_status = 'PUBLISHED', published_at = ?, updated_by = ? WHERE id = ? AND deleted = 0",
                JdbcMaps.now(), 0L, id
        );
        ensureUpdated(updated);
        return ApiResponse.success();
    }

    @PostMapping("/announcements/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable Long id) {
        int updated = jdbcTemplate.update(
                "UPDATE notice SET publish_status = 'DISABLED', show_on_home = 0, updated_by = ? WHERE id = ? AND deleted = 0",
                0L,
                id
        );
        ensureUpdated(updated);
        return ApiResponse.success();
    }

    @DeleteMapping("/announcements/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        int updated = jdbcTemplate.update(
                "UPDATE notice SET deleted = 1, updated_by = ? WHERE id = ? AND deleted = 0",
                0L,
                id
        );
        ensureUpdated(updated);
        return ApiResponse.success();
    }

    private void ensureUpdated(int updated) {
        if (updated == 0) {
            throw new BusinessException(404, "Notice not found");
        }
    }

    private String normalizedPublishStatus(Map<String, Object> body) {
        String value = JdbcMaps.strOr(body, "DRAFT", "publishStatus", "status").trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "PUBLISHED", "ACTIVE" -> "PUBLISHED";
            case "DISABLED", "INACTIVE", "OFFLINE" -> "DISABLED";
            default -> "DRAFT";
        };
    }

    private int homeCarouselFlag(Map<String, Object> body, String publishStatus) {
        return Boolean.TRUE.equals(JdbcMaps.bool(body, false, "showOnHome", "homeCarousel")) ? 1 : 0;
    }

    private String normalizedNoticeType(Map<String, Object> body) {
        String value = JdbcMaps.strOr(body, "NOTICE", "noticeType", "type").trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "ACTIVITY", "PROPERTY", "REMINDER" -> value;
            default -> "NOTICE";
        };
    }

    private void validateWindow(Map<String, Object> body) {
        String effectiveAt = JdbcMaps.datetime(body, null, "effectiveAt");
        String expiresAt = JdbcMaps.datetime(body, null, "expiresAt");
        if (effectiveAt != null && expiresAt != null && expiresAt.compareTo(effectiveAt) <= 0) {
            throw new BusinessException(400, "失效时间必须晚于生效时间");
        }
    }
}
