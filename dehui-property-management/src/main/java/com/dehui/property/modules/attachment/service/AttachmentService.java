package com.dehui.property.modules.attachment.service;

import com.dehui.property.common.OperationDict;
import com.dehui.property.common.Result;
import com.dehui.property.modules.attachment.dto.AttachmentResponse;
import com.dehui.property.modules.attachment.entity.Attachment;
import com.dehui.property.modules.attachment.repository.AttachmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttachmentService {
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024;

    private final AttachmentRepository attachmentRepository;

    public List<AttachmentResponse> list(String bizType, Long bizId) {
        return attachmentRepository.findByBizTypeAndBizIdAndDeletedFalseOrderBySortOrderAscCreatedTimeAsc(
                        normalize(bizType),
                        bizId
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<AttachmentResponse> listByCategory(String bizType, Long bizId, String fileCategory) {
        return attachmentRepository.findByBizTypeAndBizIdAndFileCategoryAndDeletedFalseOrderBySortOrderAscCreatedTimeAsc(
                        normalize(bizType),
                        bizId,
                        normalize(fileCategory)
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public Result<AttachmentResponse> upload(String bizType, Long bizId, String fileCategory, String uploadedBy, MultipartFile file) {
        if (bizId == null) {
            return Result.error("业务ID不能为空");
        }
        if (file == null || file.isEmpty()) {
            return Result.error("请选择文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.error("单个附件不能超过20MB");
        }

        String normalizedBizType = normalize(bizType);
        String normalizedCategory = normalize(fileCategory);
        String originalFilename = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String extension = resolveExtension(originalFilename);
        String storedName = UUID.randomUUID() + extension;

        try {
            Path uploadDir = Paths.get("uploads", "attachments", normalizedBizType, String.valueOf(bizId))
                    .toAbsolutePath()
                    .normalize();
            Files.createDirectories(uploadDir);
            Path target = uploadDir.resolve(storedName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            Attachment attachment = new Attachment();
            attachment.setBizType(normalizedBizType);
            attachment.setBizId(bizId);
            attachment.setFileCategory(normalizedCategory);
            attachment.setFileType(OperationDict.fileTypeFromMime(file.getContentType()));
            attachment.setFileName(originalFilename);
            attachment.setFileUrl("/uploads/attachments/" + normalizedBizType + "/" + bizId + "/" + storedName);
            attachment.setFileSize(file.getSize());
            attachment.setMimeType(file.getContentType());
            attachment.setSortOrder((int) attachmentRepository.countByBizTypeAndBizIdAndFileCategoryAndDeletedFalse(
                    normalizedBizType,
                    bizId,
                    normalizedCategory
            ) + 1);
            attachment.setUploadedBy(uploadedBy == null || uploadedBy.isBlank() ? "system" : uploadedBy.trim());
            attachment.setDeleted(false);

            return Result.success(toResponse(attachmentRepository.save(attachment)));
        } catch (IOException e) {
            return Result.error("附件保存失败");
        }
    }

    @Transactional
    public Result<Void> delete(Long id) {
        return attachmentRepository.findById(id)
                .map(attachment -> {
                    attachment.setDeleted(true);
                    attachmentRepository.save(attachment);
                    return Result.<Void>success();
                })
                .orElseGet(() -> Result.error("附件不存在"));
    }

    private AttachmentResponse toResponse(Attachment attachment) {
        AttachmentResponse response = new AttachmentResponse();
        response.setId(attachment.getId());
        response.setBizType(attachment.getBizType());
        response.setBizId(attachment.getBizId());
        response.setFileType(attachment.getFileType());
        response.setFileCategory(attachment.getFileCategory());
        response.setFileName(attachment.getFileName());
        response.setFileUrl(attachment.getFileUrl());
        response.setFileSize(attachment.getFileSize());
        response.setMimeType(attachment.getMimeType());
        response.setSortOrder(attachment.getSortOrder());
        response.setUploadedBy(attachment.getUploadedBy());
        response.setCreatedTime(attachment.getCreatedTime());
        return response;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "GENERAL" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String resolveExtension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index >= 0 && index < filename.length() - 1) {
            String extension = filename.substring(index).toLowerCase(Locale.ROOT);
            if (extension.length() <= 10) {
                return extension;
            }
        }
        return ".bin";
    }
}
