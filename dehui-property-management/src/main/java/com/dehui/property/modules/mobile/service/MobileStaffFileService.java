package com.dehui.property.modules.mobile.service;

import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcMaps;
import com.dehui.property.config.FileStorageProperties;
import com.dehui.property.modules.mobile.controller.MobileApiSupport;
import com.dehui.property.security.AuthPrincipal;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MobileStaffFileService {

    private static final long MAX_FILE_SIZE = 20L * 1024L * 1024L;
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp");
    private static final Set<String> IMAGE_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> ATTACHMENT_EXTENSIONS = Set.of(
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".txt", ".csv", ".zip", ".jpg", ".jpeg", ".png", ".webp"
    );
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            ".exe", ".dll", ".bat", ".cmd", ".com", ".js", ".jar", ".ps1", ".sh", ".msi"
    );

    private final JdbcTemplate jdbcTemplate;
    private final FileStorageProperties properties;

    public MobileStaffFileService(JdbcTemplate jdbcTemplate, FileStorageProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Transactional
    public Map<String, Object> uploadDraft(AuthPrincipal principal, MultipartFile file) {
        return uploadDraft(principal, file, "IMAGE");
    }

    @Transactional
    public Map<String, Object> uploadDraft(AuthPrincipal principal, MultipartFile file, String rawFieldType) {
        boolean staff = principal != null && "STAFF".equals(principal.userType());
        boolean tenant = principal != null && "TENANT".equals(principal.userType());
        if (!staff && !tenant) throw BusinessException.forbidden("当前身份不能上传内部业务文件");
        Long staffUserId = staff ? MobileApiSupport.requireStaffUserId(jdbcTemplate, principal) : null;
        Long mobileUserId = tenant ? principal.userId() : null;
        String fieldType = String.valueOf(rawFieldType == null ? "IMAGE" : rawFieldType).trim().toUpperCase(Locale.ROOT);
        if (!Set.of("IMAGE", "ATTACHMENT").contains(fieldType)) {
            throw new BusinessException(400, "仅支持IMAGE或ATTACHMENT字段上传");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(400, "文件大小不能超过20MB");
        }
        String originalName = safeName(file.getOriginalFilename());
        String extension = extension(originalName);
        String mimeType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT);
        if (BLOCKED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(400, "不允许上传可执行文件");
        }
        if ("IMAGE".equals(fieldType)
                && (!IMAGE_EXTENSIONS.contains(extension) || !IMAGE_MIME_TYPES.contains(mimeType))) {
            throw new BusinessException(400, "仅支持jpg/jpeg/png/webp图片，且扩展名必须与文件类型一致");
        }
        if ("ATTACHMENT".equals(fieldType) && !ATTACHMENT_EXTENSIONS.contains(extension)) {
            throw new BusinessException(400, "附件类型不受支持");
        }

        String storedName = "mobile_" + UUID.randomUUID().toString().replace("-", "") + extension;
        Path uploadRoot = uploadRoot();
        Path directory = uploadRoot.resolve("private").resolve("mobile").normalize();
        if (!directory.startsWith(uploadRoot)) {
            throw new BusinessException(500, "内部文件目录配置无效");
        }
        try {
            Files.createDirectories(directory);
            Path target = directory.resolve(storedName).normalize();
            if (!target.startsWith(directory)) {
                throw new BusinessException(400, "文件名无效");
            }
            file.transferTo(target);
        } catch (IOException exception) {
            throw new BusinessException(500, "内部图片保存失败");
        }

        String code = JdbcMaps.code("FILE");
        jdbcTemplate.update(
                """
                INSERT INTO file_upload (
                    code, biz_type, biz_id, original_name, stored_name, storage_path,
                    workspace_key, access_scope, owner_sys_user_id, owner_mobile_user_id, mime_type, file_size,
                    status, created_by, updated_by, deleted
                )
                VALUES (?, ?, NULL, ?, ?, ?, NULL, 'INTERNAL', ?, ?, ?, ?,
                        'ENABLED', ?, ?, 0)
                """,
                code,
                staff ? "STAFF_DRAFT" : "MOBILE_DRAFT",
                originalName,
                storedName,
                "private/mobile/" + storedName,
                staffUserId,
                mobileUserId,
                mimeType,
                file.getSize(),
                staff ? staffUserId : mobileUserId,
                staff ? staffUserId : mobileUserId
        );
        Long fileId = jdbcTemplate.queryForObject(
                "SELECT id FROM file_upload WHERE code = ? AND deleted = 0 LIMIT 1",
                Long.class,
                code
        );
        return Map.of(
                "id", fileId,
                "fileId", fileId,
                "name", originalName,
                "url", "/api/mobile/files/" + fileId,
                "mimeType", mimeType,
                "size", file.getSize()
        );
    }

    @Transactional
    public void claimDraftFiles(
            List<Long> fileIds,
            String bizType,
            Long bizId,
            Long staffUserId
    ) {
        List<Long> ids = normalizedIds(fileIds);
        if (ids.isEmpty()) return;
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.addAll(ids);
        args.add(staffUserId);
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM file_upload
                WHERE id IN (%s)
                  AND access_scope = 'INTERNAL'
                  AND biz_type = 'STAFF_DRAFT'
                  AND biz_id IS NULL
                  AND owner_sys_user_id = ?
                  AND status = 'ENABLED'
                  AND deleted = 0
                """.formatted(placeholders),
                Long.class,
                args.toArray()
        );
        if (count == null || count != ids.size()) {
            throw new BusinessException(400, "图片不存在、已被使用或不属于当前员工");
        }
        List<Object> updateArgs = new ArrayList<>();
        updateArgs.add(bizType);
        updateArgs.add(bizId);
        updateArgs.add(staffUserId);
        updateArgs.addAll(ids);
        int updated = jdbcTemplate.update(
                """
                UPDATE file_upload
                SET biz_type = ?, biz_id = ?, updated_by = ?
                WHERE id IN (%s)
                  AND biz_type = 'STAFF_DRAFT'
                  AND access_scope = 'INTERNAL'
                  AND deleted = 0
                """.formatted(placeholders),
                updateArgs.toArray()
        );
        if (updated != ids.size()) {
            throw new BusinessException(409, "图片绑定状态已变化，请重新选择");
        }
    }

    @Transactional
    public void claimDraftFiles(
            AuthPrincipal principal,
            List<Long> fileIds,
            String bizType,
            Long bizId,
            String workspaceKey
    ) {
        List<Long> ids = normalizedIds(fileIds);
        if (ids.isEmpty()) return;
        boolean staff = principal != null && "STAFF".equals(principal.userType());
        boolean tenant = principal != null && "TENANT".equals(principal.userType());
        if (!staff && !tenant) throw BusinessException.forbidden("当前身份不能绑定内部业务文件");
        Long staffUserId = staff ? MobileApiSupport.requireStaffUserId(jdbcTemplate, principal) : null;
        Long mobileUserId = tenant ? principal.userId() : null;
        String draftType = staff ? "STAFF_DRAFT" : "MOBILE_DRAFT";
        String ownerColumn = staff ? "owner_sys_user_id" : "owner_mobile_user_id";
        Long ownerId = staff ? staffUserId : mobileUserId;
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        List<Object> args = new ArrayList<>(ids);
        args.add(ownerId);
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM file_upload
                WHERE id IN (%s) AND access_scope = 'INTERNAL'
                  AND biz_type = ? AND biz_id IS NULL
                  AND %s = ? AND status = 'ENABLED' AND deleted = 0
                """.formatted(placeholders, ownerColumn),
                Long.class,
                concat(ids, draftType, ownerId)
        );
        if (count == null || count != ids.size()) {
            throw new BusinessException(400, "文件不存在、已被使用或不属于当前用户");
        }
        List<Object> updateArgs = new ArrayList<>();
        updateArgs.add(bizType);
        updateArgs.add(bizId);
        updateArgs.add(workspaceKey);
        updateArgs.add(ownerId);
        updateArgs.addAll(ids);
        int updated = jdbcTemplate.update(
                """
                UPDATE file_upload SET biz_type = ?, biz_id = ?, workspace_key = ?, updated_by = ?
                WHERE id IN (%s) AND biz_type = ? AND access_scope = 'INTERNAL' AND deleted = 0
                """.formatted(placeholders),
                concat(List.of(bizType, bizId, workspaceKey, ownerId), ids, draftType)
        );
        if (updated != ids.size()) throw new BusinessException(409, "文件绑定状态已变化，请重新选择");
    }

    public FileDownload requireDownload(AuthPrincipal principal, Long fileId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, biz_type AS bizType, biz_id AS bizId, workspace_key AS workspaceKey, storage_path AS storagePath,
                       access_scope AS accessScope, owner_sys_user_id AS ownerSysUserId,
                       owner_mobile_user_id AS ownerMobileUserId,
                       original_name AS originalName, mime_type AS mimeType
                FROM file_upload
                WHERE id = ? AND status = 'ENABLED' AND deleted = 0
                LIMIT 1
                """,
                fileId
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "文件不存在");
        }
        Map<String, Object> file = rows.getFirst();
        if (!canRead(principal, file)) {
            throw new BusinessException(404, "文件不存在或无权访问");
        }
        Path uploadRoot = uploadRoot();
        String storagePath = String.valueOf(file.get("storagePath"));
        Path target = storagePath.startsWith("/files/")
                ? uploadRoot.resolve(storagePath.substring("/files/".length()))
                : uploadRoot.resolve(storagePath);
        target = target.toAbsolutePath().normalize();
        if (!target.startsWith(uploadRoot) || !Files.isRegularFile(target)) {
            throw new BusinessException(404, "文件不存在");
        }
        Resource resource = new FileSystemResource(target);
        String mimeType = file.get("mimeType") == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : String.valueOf(file.get("mimeType"));
        return new FileDownload(resource, mimeType, String.valueOf(file.get("originalName")));
    }

    private boolean canRead(AuthPrincipal principal, Map<String, Object> file) {
        if (principal == null) return false;
        String bizType = String.valueOf(file.get("bizType"));
        Long bizId = longValue(file.get("bizId"));
        if ("CUSTOM_FIELD".equals(bizType)) {
            bizType = switch (String.valueOf(file.get("workspaceKey"))) {
                case "inspections" -> "INSPECTION";
                case "workorders" -> "WORK_ORDER";
                case "visitors" -> "VISITOR";
                default -> "UNSUPPORTED_CUSTOM_FIELD";
            };
        }
        if ("SYSTEM".equals(principal.userType())) {
            if (principal.roles() != null && principal.roles().contains("SUPER_ADMIN")) return true;
            if ("CUSTOM_FIELD_IMAGE".equals(bizType) || "CUSTOM_FIELD_ATTACHMENT".equals(bizType)) {
                return principal.userId() != null
                        && principal.userId().equals(longValue(file.get("ownerSysUserId")));
            }
            if ("WORK_ORDER".equals(bizType)) {
                if (!hasPermission(principal, "workorder:view")) return false;
                if (hasPermission(principal, "workorder:assign")) return true;
                if (bizId == null || principal.userId() == null) return false;
                Long count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM work_order WHERE id = ? AND assignee_id = ? AND deleted = 0",
                        Long.class,
                        bizId,
                        principal.userId()
                );
                return count != null && count > 0;
            }
            String required = switch (bizType) {
                case "INSPECTION" -> "inspection:view";
                case "VISITOR" -> "visitor:view";
                default -> null;
            };
            if (required != null) return principal.permissions().contains(required);
            return "ADMIN_DRAFT".equals(bizType)
                    && principal.userId().equals(longValue(file.get("ownerSysUserId")));
        }
        if ("STAFF".equals(principal.userType())) {
            Long staffUserId = MobileApiSupport.requireStaffUserId(jdbcTemplate, principal);
            if ("STAFF_DRAFT".equals(bizType)) {
                return staffUserId.equals(longValue(file.get("ownerSysUserId")));
            }
            if ("INSPECTION".equals(bizType)) {
                return principal.permissions().contains("inspection.staff.view");
            }
            if ("ENERGY_READING".equals(bizType)) {
                return principal.permissions().contains("energy.staff.view");
            }
            if ("WORK_ORDER".equals(bizType)) {
                if (!principal.permissions().contains("workorder.staff.view")) return false;
                Long count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM work_order WHERE id = ? AND assignee_id = ? AND deleted = 0",
                        Long.class,
                        bizId,
                        staffUserId
                );
                return count != null && count > 0;
            }
            if ("VISITOR".equals(bizType)) {
                return principal.permissions().contains("visitor.staff.view");
            }
            return false;
        }
        if (!"TENANT".equals(principal.userType())) {
            return false;
        }
        if ("MOBILE_DRAFT".equals(bizType)) {
            return principal.userId().equals(longValue(file.get("ownerMobileUserId")));
        }
        Long tenantId = MobileApiSupport.requireTenantId(principal);
        if ("TENANT_LOGO".equals(bizType)) {
            if (!tenantId.equals(bizId)) return false;
            Long fileId = longValue(file.get("id"));
            Long count = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM tenant
                    WHERE id = ?
                      AND logo_file_id = ?
                      AND status IN ('ACTIVE', 'ENABLED')
                      AND deleted = 0
                    """,
                    Long.class,
                    tenantId,
                    fileId
            );
            return count != null && count > 0;
        }
        boolean companyScope = MobileApiSupport.isTenantManager(jdbcTemplate, principal);
        if ("VISITOR".equals(bizType)) {
            Long count = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*) FROM visitor_appointment
                    WHERE id = ? AND tenant_id = ?
                      AND (? = 1 OR applicant_mobile_user_id = ?)
                      AND deleted = 0
                    """,
                    Long.class, bizId, tenantId, companyScope ? 1 : 0, principal.userId()
            );
            return count != null && count > 0;
        }
        if (!"WORK_ORDER".equals(bizType)) return false;
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM work_order
                WHERE id = ?
                  AND tenant_id = ?
                  AND (? = 1 OR submitter_mobile_user_id = ?)
                  AND deleted = 0
                """,
                Long.class,
                bizId,
                tenantId,
                companyScope ? 1 : 0,
                principal.userId()
        );
        return count != null && count > 0;
    }

    private boolean hasPermission(AuthPrincipal principal, String required) {
        if (principal.permissions() == null) return false;
        int separator = required.indexOf(':');
        String moduleWildcard = separator > 0 ? required.substring(0, separator) + ":*" : "";
        return principal.permissions().contains(required)
                || (!moduleWildcard.isEmpty() && principal.permissions().contains(moduleWildcard))
                || principal.permissions().contains("*");
    }

    private List<Long> normalizedIds(List<Long> ids) {
        if (ids == null) return List.of();
        return ids.stream().filter(id -> id != null && id > 0).distinct().toList();
    }

    private Object[] concat(List<?> left, Object... right) {
        List<Object> result = new ArrayList<>(left);
        java.util.Collections.addAll(result, right);
        return result.toArray();
    }

    private Object[] concat(List<?> left, List<?> middle, Object... right) {
        List<Object> result = new ArrayList<>(left);
        result.addAll(middle);
        java.util.Collections.addAll(result, right);
        return result.toArray();
    }

    private Path uploadRoot() {
        return Path.of(properties.uploadDir()).toAbsolutePath().normalize();
    }

    private String safeName(String value) {
        String name = value == null ? "image" : value.strip();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        return name.isBlank() ? "image" : name;
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot).toLowerCase(Locale.ROOT);
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null || String.valueOf(value).isBlank()) return null;
        return Long.valueOf(String.valueOf(value));
    }

    public record FileDownload(Resource resource, String mimeType, String originalName) {
    }
}
