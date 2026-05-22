package com.dehui.property.modules.notice.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcMaps;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class NoticeController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping({"/notices", "/announcements", "/mobile/announcements"})
    public ApiResponse<List<Map<String, Object>>> notices() {
        return ApiResponse.success(jdbcTemplate.queryForList(
                """
                SELECT id, code, title, content, notice_type AS noticeType, publish_status AS publishStatus,
                       published_at AS publishedAt, status, remark
                FROM notice
                WHERE deleted = 0
                ORDER BY updated_at DESC, id DESC
                """
        ));
    }

    @PostMapping("/announcements")
    public ApiResponse<Void> create(@RequestBody Map<String, Object> body) {
        jdbcTemplate.update(
                """
                INSERT INTO notice (code, title, content, notice_type, publish_status, status, created_by, updated_by, remark)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                """,
                JdbcMaps.code("NOT"),
                JdbcMaps.requiredStr(body, "Title is required", "title"),
                JdbcMaps.strOr(body, "", "content"),
                JdbcMaps.strOr(body, "GENERAL", "noticeType", "type"),
                JdbcMaps.strOr(body, "DRAFT", "publishStatus", "status"),
                0L,
                0L,
                JdbcMaps.str(body, "remark")
        );
        return ApiResponse.success();
    }

    @PutMapping("/announcements/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        int updated = jdbcTemplate.update(
                """
                UPDATE notice
                SET title = ?, content = ?, notice_type = ?, publish_status = ?, updated_by = ?, remark = ?
                WHERE id = ? AND deleted = 0
                """,
                JdbcMaps.requiredStr(body, "Title is required", "title"),
                JdbcMaps.strOr(body, "", "content"),
                JdbcMaps.strOr(body, "GENERAL", "noticeType", "type"),
                JdbcMaps.strOr(body, "DRAFT", "publishStatus", "status"),
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

    private void ensureUpdated(int updated) {
        if (updated == 0) {
            throw new BusinessException(404, "Notice not found");
        }
    }
}
