package com.dehui.property.modules.tenant.application;

import com.dehui.property.common.BusinessException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantLogoService {

    private static final Set<String> IMAGE_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public Map<String, Object> updateLogo(Long tenantId, Long logoFileId, Long operatorId) {
        List<Map<String, Object>> tenants = jdbcTemplate.queryForList(
                """
                SELECT logo_file_id AS logoFileId
                FROM tenant
                WHERE id = ? AND deleted = 0
                FOR UPDATE
                """,
                tenantId
        );
        if (tenants.isEmpty()) {
            throw new BusinessException(404, "租户不存在");
        }

        Long previousLogoFileId = longValue(tenants.getFirst().get("logoFileId"));
        LogoFile nextLogo = logoFileId == null
                ? null
                : requireAssignableLogo(tenantId, previousLogoFileId, logoFileId, operatorId);

        if (nextLogo != null && nextLogo.draft()) {
            int claimed = jdbcTemplate.update(
                    """
                    UPDATE file_upload
                    SET biz_type = 'TENANT_LOGO',
                        biz_id = ?,
                        workspace_key = 'tenants',
                        updated_by = ?
                    WHERE id = ?
                      AND biz_type = 'CUSTOM_FIELD_IMAGE'
                      AND biz_id IS NULL
                      AND workspace_key = 'tenants'
                      AND access_scope = 'INTERNAL'
                      AND owner_sys_user_id = ?
                      AND status = 'ENABLED'
                      AND deleted = 0
                    """,
                    tenantId,
                    operatorId,
                    logoFileId,
                    operatorId
            );
            if (claimed != 1) {
                throw new BusinessException(409, "Logo 文件状态已变化，请重新上传");
            }
        }

        int updated = jdbcTemplate.update(
                """
                UPDATE tenant
                SET logo_file_id = ?, updated_by = ?
                WHERE id = ? AND deleted = 0
                """,
                logoFileId,
                operatorId,
                tenantId
        );
        if (updated != 1) {
            throw new BusinessException(404, "租户不存在");
        }

        if (previousLogoFileId != null && !previousLogoFileId.equals(logoFileId)) {
            jdbcTemplate.update(
                    """
                    UPDATE file_upload
                    SET status = 'DISABLED', updated_by = ?
                    WHERE id = ?
                      AND biz_type = 'TENANT_LOGO'
                      AND biz_id = ?
                      AND workspace_key = 'tenants'
                      AND access_scope = 'INTERNAL'
                      AND deleted = 0
                    """,
                    operatorId,
                    previousLogoFileId,
                    tenantId
            );
        }

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("logoFileId", logoFileId);
        result.put("logoFileName", nextLogo == null ? "" : nextLogo.originalName());
        result.put("logoUrl", logoFileId == null ? "" : "/api/files/" + logoFileId);
        return result;
    }

    private LogoFile requireAssignableLogo(
            Long tenantId,
            Long currentLogoFileId,
            Long logoFileId,
            Long operatorId
    ) {
        List<Map<String, Object>> files = jdbcTemplate.queryForList(
                """
                SELECT id,
                       biz_type AS bizType,
                       biz_id AS bizId,
                       workspace_key AS workspaceKey,
                       access_scope AS accessScope,
                       owner_sys_user_id AS ownerSysUserId,
                       original_name AS originalName,
                       mime_type AS mimeType
                FROM file_upload
                WHERE id = ?
                  AND status = 'ENABLED'
                  AND deleted = 0
                FOR UPDATE
                """,
                logoFileId
        );
        if (files.isEmpty()) {
            throw invalidLogo();
        }

        Map<String, Object> file = files.getFirst();
        String mimeType = text(file.get("mimeType")).toLowerCase(Locale.ROOT);
        boolean tenantWorkspace = "tenants".equals(text(file.get("workspaceKey")));
        boolean internal = "INTERNAL".equals(text(file.get("accessScope")));
        Long bizId = longValue(file.get("bizId"));
        String bizType = text(file.get("bizType"));
        boolean ownedDraft = "CUSTOM_FIELD_IMAGE".equals(bizType)
                && bizId == null
                && operatorId != null
                && operatorId.equals(longValue(file.get("ownerSysUserId")));
        boolean alreadyAssigned = "TENANT_LOGO".equals(bizType)
                && tenantId.equals(bizId)
                && logoFileId.equals(currentLogoFileId);

        if (!tenantWorkspace || !internal || !IMAGE_MIME_TYPES.contains(mimeType)
                || (!ownedDraft && !alreadyAssigned)) {
            throw invalidLogo();
        }
        return new LogoFile(text(file.get("originalName")), ownedDraft);
    }

    private BusinessException invalidLogo() {
        return new BusinessException(400, "Logo 文件不存在、已被使用或不属于当前租户工作区");
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Long.valueOf(String.valueOf(value));
    }

    private record LogoFile(String originalName, boolean draft) {
    }
}
