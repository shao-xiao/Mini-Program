package com.dehui.property.modules.attachment.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Entity
@Table(name = "attachments")
@EqualsAndHashCode(callSuper = true)
public class Attachment extends BaseEntity {
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

    @Column(nullable = false)
    private Boolean deleted = false;
}
