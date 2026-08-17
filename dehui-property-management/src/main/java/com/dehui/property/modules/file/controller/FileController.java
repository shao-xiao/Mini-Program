package com.dehui.property.modules.file.controller;

import com.dehui.property.config.FileStorageProperties;
import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcMaps;
import com.dehui.property.modules.customfield.application.CustomFieldAuthorizationService;
import com.dehui.property.modules.customfield.application.CustomFieldWorkspaceRegistry;
import com.dehui.property.modules.customfield.domain.CustomFieldType;
import com.dehui.property.modules.mobile.service.MobileStaffFileService;
import com.dehui.property.security.AuthInterceptor;
import com.dehui.property.security.AuthPrincipal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class FileController {

    private static final long MAX_FILE_SIZE = 20L * 1024L * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp");
    private static final Set<String> CUSTOM_IMAGE_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp");
    private static final Set<String> CUSTOM_ATTACHMENT_EXTENSIONS = Set.of(
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt", ".csv",
            ".jpg", ".jpeg", ".png", ".webp", ".zip"
    );
    private static final Set<String> DANGEROUS_EXTENSIONS = Set.of(
            ".exe", ".dll", ".bat", ".cmd", ".com", ".scr", ".msi", ".ps1", ".sh",
            ".php", ".jsp", ".js", ".html", ".svg"
    );
    private static final Set<String> IMAGE_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> ATTACHMENT_MIME_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain", "text/csv",
            "image/jpeg", "image/png", "image/webp",
            "application/zip", "application/x-zip-compressed"
    );

    private final JdbcTemplate jdbcTemplate;
    private final FileStorageProperties fileStorageProperties;
    private final CustomFieldWorkspaceRegistry customFieldWorkspaceRegistry;
    private final CustomFieldAuthorizationService customFieldAuthorization;
    private MobileStaffFileService mobileStaffFileService;

    public FileController(
            JdbcTemplate jdbcTemplate,
            FileStorageProperties fileStorageProperties,
            CustomFieldWorkspaceRegistry customFieldWorkspaceRegistry,
            CustomFieldAuthorizationService customFieldAuthorization
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileStorageProperties = fileStorageProperties;
        this.customFieldWorkspaceRegistry = customFieldWorkspaceRegistry;
        this.customFieldAuthorization = customFieldAuthorization;
    }

    @Autowired(required = false)
    void setMobileStaffFileService(MobileStaffFileService service) {
        this.mobileStaffFileService = service;
    }

    @PostMapping("/files/upload")
    public ApiResponse<Map<String, Object>> upload(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "fieldType", required = false) String fieldType,
            @RequestParam(value = "workspaceKey", required = false) String workspaceKey
    ) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(400, "图片大小不能超过20MB");
        }

        String originalName = normalizeOriginalName(file.getOriginalFilename());
        CustomFieldType customFieldType = fieldType == null || fieldType.isBlank()
                ? null
                : CustomFieldType.fromWireName(fieldType);
        if (customFieldType != null) {
            if (customFieldType != CustomFieldType.IMAGE && customFieldType != CustomFieldType.ATTACHMENT) {
                throw new BusinessException(400, "仅图片和附件字段支持文件上传");
            }
            if (workspaceKey == null || workspaceKey.isBlank()) {
                throw new BusinessException(400, "自定义字段上传必须指定工作区");
            }
            customFieldAuthorization.requireValueUpdate(principal, customFieldWorkspaceRegistry.require(workspaceKey));
        }
        String extension = customFieldType == null
                ? extensionOrNull(originalName, file.getContentType())
                : customFieldExtensionOrNull(originalName, file.getContentType(), customFieldType);
        if (extension == null) {
            throw new BusinessException(400, "仅支持 jpg/jpeg/png/gif/webp/bmp 图片上传");
        }

        try {
            Path uploadRoot = Path.of(fileStorageProperties.uploadDir()).toAbsolutePath().normalize();
            boolean internal = customFieldType != null;
            Path directory = uploadRoot
                    .resolve(internal ? "private" : "public")
                    .resolve(internal ? "custom-fields" : "attachments")
                    .normalize();
            if (!directory.startsWith(uploadRoot)) {
                throw new BusinessException(500, "文件目录配置无效");
            }
            Files.createDirectories(directory);

            String storedName = "att_" + UUID.randomUUID().toString().replace("-", "") + extension;
            Path target = directory.resolve(storedName);
            file.transferTo(target);

            String storagePath = internal
                    ? "private/custom-fields/" + storedName
                    : "/files/public/attachments/" + storedName;
            String fileCode = JdbcMaps.code("FILE");
            Long actorId = actorId(principal);
            jdbcTemplate.update(
                    """
                    INSERT INTO file_upload
                        (code, biz_type, biz_id, workspace_key, original_name, stored_name, storage_path,
                         access_scope, owner_sys_user_id, mime_type, file_size, status, created_by, updated_by)
                    VALUES (?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, 'ENABLED', ?, ?)
                    """,
                    fileCode,
                    customFieldType == CustomFieldType.IMAGE ? "CUSTOM_FIELD_IMAGE" : "CUSTOM_FIELD_ATTACHMENT",
                    internal ? workspaceKey : null,
                    originalName,
                    storedName,
                    storagePath,
                    internal ? "INTERNAL" : "PUBLIC",
                    internal ? actorId : null,
                    file.getContentType(),
                    file.getSize(),
                    actorId,
                    actorId
            );

            Long fileId = jdbcTemplate.queryForObject(
                    "SELECT id FROM file_upload WHERE code = ? AND deleted = 0 LIMIT 1",
                    Long.class,
                    fileCode
            );
            if (fileId == null) {
                throw new SQLException("上传文件记录不存在");
            }

            return ApiResponse.success(Map.of(
                    "id", fileId,
                    "fileId", fileId,
                    "name", originalName,
                    "url", internal ? "/api/files/" + fileId : storagePath,
                    "mimeType", file.getContentType() == null ? "" : file.getContentType(),
                    "size", file.getSize()
            ));
        } catch (SQLException exception) {
            throw new BusinessException(500, "文件上传信息入库失败");
        } catch (Exception exception) {
            throw new BusinessException(500, "图片上传失败");
        }
    }

    @GetMapping("/files/{id}")
    public ResponseEntity<org.springframework.core.io.Resource> download(
            @PathVariable Long id,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        BusinessException mobileDownloadFailure = null;
        if (principal != null && mobileStaffFileService != null) {
            try {
                return mobileFileResponse(mobileStaffFileService.requireDownload(principal, id));
            } catch (BusinessException exception) {
                if (!"SYSTEM".equals(principal.userType()) || exception.getCode() != 404) {
                    throw exception;
                }
                mobileDownloadFailure = exception;
            }
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, biz_type AS bizType, biz_id AS bizId,
                       workspace_key AS workspaceKey, storage_path AS storagePath,
                       access_scope AS accessScope, original_name AS originalName, mime_type AS mimeType
                FROM file_upload
                WHERE id = ? AND status = 'ENABLED' AND deleted = 0
                """,
                id
        );
        if (rows.isEmpty()) throw new BusinessException(404, "文件不存在");
        Map<String, Object> row = rows.getFirst();
        String workspaceKey = row.get("workspaceKey") == null ? null : String.valueOf(row.get("workspaceKey"));
        boolean workOrderFile = "WORK_ORDER".equals(String.valueOf(row.get("bizType")))
                || ("CUSTOM_FIELD".equals(String.valueOf(row.get("bizType")))
                    && "workorders".equals(workspaceKey));
        boolean ownerScopedDraft = "CUSTOM_FIELD_IMAGE".equals(String.valueOf(row.get("bizType")))
                || "CUSTOM_FIELD_ATTACHMENT".equals(String.valueOf(row.get("bizType")));
        if ((workOrderFile || ownerScopedDraft)
                && (mobileStaffFileService == null || mobileDownloadFailure != null)) {
            if (mobileDownloadFailure != null) {
                throw mobileDownloadFailure;
            }
            throw new BusinessException(404, "File not found");
        }
        if ("INTERNAL".equals(String.valueOf(row.get("accessScope")))) {
            if (workspaceKey == null || !customFieldWorkspaceRegistry.supports(workspaceKey)) {
                throw new BusinessException(404, "文件不存在或无权访问");
            }
            customFieldAuthorization.requireView(principal, customFieldWorkspaceRegistry.require(workspaceKey));
        }
        Path uploadRoot = Path.of(fileStorageProperties.uploadDir()).toAbsolutePath().normalize();
        String storagePath = String.valueOf(row.get("storagePath"));
        Path target = storagePath.startsWith("/files/")
                ? uploadRoot.resolve(storagePath.substring("/files/".length()))
                : uploadRoot.resolve(storagePath);
        target = target.toAbsolutePath().normalize();
        if (!target.startsWith(uploadRoot) || !Files.isRegularFile(target)) {
            throw new BusinessException(404, "文件不存在");
        }
        String mime = row.get("mimeType") == null
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : String.valueOf(row.get("mimeType"));
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(mime);
        } catch (RuntimeException ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(String.valueOf(row.get("originalName")), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(new FileSystemResource(target));
    }

    private ResponseEntity<org.springframework.core.io.Resource> mobileFileResponse(
            MobileStaffFileService.FileDownload file
    ) {
        MediaType type;
        try {
            type = MediaType.parseMediaType(file.mimeType());
        } catch (RuntimeException ignored) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(type)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(file.originalName(), StandardCharsets.UTF_8).build().toString()
                )
                .body(file.resource());
    }

    private Long actorId(AuthPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            return 0L;
        }
        return principal.userId();
    }

    private String normalizeOriginalName(String originalName) {
        String normalized = originalName == null ? "" : originalName.strip();
        int linuxIndex = normalized.lastIndexOf('/');
        int windowsIndex = normalized.lastIndexOf('\\');
        int begin = Math.max(linuxIndex, windowsIndex);
        if (begin >= 0) {
            normalized = normalized.substring(begin + 1);
        }
        return normalized.isBlank() ? "attachment" : normalized;
    }

    private String extensionOrNull(String filename, String contentType) {
        String extension = extension(filename);
        if (ALLOWED_EXTENSIONS.contains(extension)) {
            return extension;
        }
        if (contentType == null) {
            return null;
        }
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "image/bmp" -> ".bmp";
            default -> null;
        };
    }

    private String customFieldExtensionOrNull(String filename, String contentType, CustomFieldType fieldType) {
        String extension = extension(filename);
        String mimeType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (DANGEROUS_EXTENSIONS.contains(extension)) {
            throw new BusinessException(400, "该文件类型存在安全风险，禁止上传");
        }
        Set<String> extensions = fieldType == CustomFieldType.IMAGE
                ? CUSTOM_IMAGE_EXTENSIONS
                : CUSTOM_ATTACHMENT_EXTENSIONS;
        Set<String> mimeTypes = fieldType == CustomFieldType.IMAGE
                ? IMAGE_MIME_TYPES
                : ATTACHMENT_MIME_TYPES;
        return extensions.contains(extension) && mimeTypes.contains(mimeType) ? extension : null;
    }

    private String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int index = filename.lastIndexOf('.');
        if (index < 0) {
            return "";
        }
        return filename.substring(index).toLowerCase(Locale.ROOT);
    }
}
