package com.dehui.property.modules.attachment.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AttachmentResponse {
    private Long id;
    private String bizType;
    private Long bizId;
    private String fileType;
    private String fileCategory;
    private String fileName;
    private String fileUrl;
    private Long fileSize;
    private String mimeType;
    private Integer sortOrder;
    private String uploadedBy;
    private LocalDateTime createdTime;
}
