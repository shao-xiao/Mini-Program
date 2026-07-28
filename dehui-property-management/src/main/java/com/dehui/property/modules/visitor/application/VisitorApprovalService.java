package com.dehui.property.modules.visitor.application;

import com.dehui.property.common.BusinessException;
import com.dehui.property.security.AuthPrincipal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VisitorApprovalService {

    private final JdbcTemplate jdbcTemplate;

    public VisitorApprovalService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Map<String, Object> approve(Long appointmentId, String reviewRemark, AuthPrincipal principal) {
        Map<String, Object> appointment = lockAppointment(appointmentId);
        requireStatus(appointment, "PENDING", "仅待审核预约可以通过");
        String remark = normalizedOptionalRemark(reviewRemark);
        int updated = jdbcTemplate.update(
                """
                UPDATE visitor_appointment
                SET appointment_status = 'REGISTERED',
                    reviewed_by = ?, reviewed_at = NOW(), review_remark = ?, updated_by = ?
                WHERE id = ? AND appointment_status = 'PENDING' AND deleted = 0
                """,
                principal.userId(), remark, principal.userId(), appointmentId
        );
        if (updated != 1) {
            throw new BusinessException(409, "预约审核状态已变化，请刷新后重试");
        }
        return result(appointmentId, "REGISTERED", remark, null);
    }

    @Transactional
    public Map<String, Object> reject(Long appointmentId, String reviewRemark, AuthPrincipal principal) {
        Map<String, Object> appointment = lockAppointment(appointmentId);
        requireStatus(appointment, "PENDING", "仅待审核预约可以驳回");
        String remark = requiredRemark(reviewRemark);
        int updated = jdbcTemplate.update(
                """
                UPDATE visitor_appointment
                SET appointment_status = 'REJECTED',
                    reviewed_by = ?, reviewed_at = NOW(), review_remark = ?, updated_by = ?
                WHERE id = ? AND appointment_status = 'PENDING' AND deleted = 0
                """,
                principal.userId(), remark, principal.userId(), appointmentId
        );
        if (updated != 1) {
            throw new BusinessException(409, "预约审核状态已变化，请刷新后重试");
        }
        return result(appointmentId, "REJECTED", remark, null);
    }

    @Transactional
    public Map<String, Object> attachQrCode(Long appointmentId, Long fileId, AuthPrincipal principal) {
        if (fileId == null || fileId <= 0) {
            throw new BusinessException(400, "请选择有效的二维码图片");
        }
        Map<String, Object> appointment = lockAppointment(appointmentId);
        requireStatus(appointment, "REGISTERED", "仅审核通过且尚未入场的预约可以上传二维码");
        Long oldFileId = longValue(appointment.get("qrCodeFileId"));

        List<Map<String, Object>> files = jdbcTemplate.queryForList(
                """
                SELECT id, original_name AS originalName, mime_type AS mimeType, file_size AS fileSize
                FROM file_upload
                WHERE id = ?
                  AND biz_type = 'CUSTOM_FIELD_IMAGE'
                  AND biz_id IS NULL
                  AND workspace_key = 'visitors'
                  AND access_scope = 'INTERNAL'
                  AND owner_sys_user_id = ?
                  AND mime_type IN ('image/jpeg', 'image/png', 'image/webp')
                  AND status = 'ENABLED'
                  AND deleted = 0
                FOR UPDATE
                """,
                fileId,
                principal.userId()
        );
        if (files.isEmpty()) {
            throw new BusinessException(400, "二维码图片不存在、已被使用或不属于当前操作人");
        }

        int claimed = jdbcTemplate.update(
                """
                UPDATE file_upload
                SET biz_type = 'VISITOR', biz_id = ?, updated_by = ?
                WHERE id = ?
                  AND biz_type = 'CUSTOM_FIELD_IMAGE'
                  AND biz_id IS NULL
                  AND workspace_key = 'visitors'
                  AND owner_sys_user_id = ?
                  AND status = 'ENABLED'
                  AND deleted = 0
                """,
                appointmentId,
                principal.userId(),
                fileId,
                principal.userId()
        );
        if (claimed != 1) {
            throw new BusinessException(409, "二维码图片绑定状态已变化，请重新选择");
        }

        int updated = jdbcTemplate.update(
                """
                UPDATE visitor_appointment
                SET qr_code_file_id = ?, qr_uploaded_by = ?, qr_uploaded_at = NOW(), updated_by = ?
                WHERE id = ? AND appointment_status = 'REGISTERED' AND deleted = 0
                """,
                fileId,
                principal.userId(),
                principal.userId(),
                appointmentId
        );
        if (updated != 1) {
            throw new BusinessException(409, "预约状态已变化，二维码未保存");
        }

        if (oldFileId != null && !oldFileId.equals(fileId)) {
            jdbcTemplate.update(
                    """
                    UPDATE file_upload
                    SET status = 'DISABLED', updated_by = ?
                    WHERE id = ? AND biz_type = 'VISITOR' AND biz_id = ? AND deleted = 0
                    """,
                    principal.userId(),
                    oldFileId,
                    appointmentId
            );
        }

        Map<String, Object> file = files.getFirst();
        Map<String, Object> response = result(appointmentId, "REGISTERED", null, fileId);
        response.put("name", file.get("originalName"));
        response.put("mimeType", file.get("mimeType"));
        response.put("size", file.get("fileSize"));
        response.put("url", "/api/files/" + fileId);
        return response;
    }

    private Map<String, Object> lockAppointment(Long appointmentId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, appointment_status AS status, qr_code_file_id AS qrCodeFileId
                FROM visitor_appointment
                WHERE id = ? AND deleted = 0
                FOR UPDATE
                """,
                appointmentId
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "访客预约不存在");
        }
        return rows.getFirst();
    }

    private void requireStatus(Map<String, Object> appointment, String expected, String message) {
        if (!expected.equals(String.valueOf(appointment.get("status")))) {
            throw new BusinessException(409, message);
        }
    }

    private String requiredRemark(String value) {
        String normalized = normalizedOptionalRemark(value);
        if (normalized == null) {
            throw new BusinessException(400, "请填写驳回原因");
        }
        return normalized;
    }

    private String normalizedOptionalRemark(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.strip();
        if (normalized.length() > 500) {
            throw new BusinessException(400, "审核意见不能超过500字");
        }
        return normalized;
    }

    private Map<String, Object> result(Long appointmentId, String status, String remark, Long fileId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", appointmentId);
        result.put("status", status);
        result.put("reviewRemark", remark);
        result.put("qrCodeFileId", fileId);
        return result;
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null || String.valueOf(value).isBlank()) return null;
        return Long.valueOf(String.valueOf(value));
    }
}
