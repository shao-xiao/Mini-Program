package com.dehui.property.modules.announcement.entity;

import com.dehui.property.common.BaseEntity;
import com.dehui.property.common.OperationDict;
import com.dehui.property.modules.attachment.dto.AttachmentResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "announcement")
@EqualsAndHashCode(callSuper = true)
public class Announcement extends BaseEntity {

    private String title;

    private String content;

    private String type; // NOTICE / MAINTENANCE / PAYMENT / EVENT

    private String status; // DRAFT / PUBLISHED / ARCHIVED

    private String targetType;

    private Long targetTenantId;

    private Long targetFloorId;

    private Long targetRoomId;

    private Boolean pinned;

    private LocalDateTime publishTime;

    private LocalDateTime offlineTime;

    private LocalDateTime deletedAt;

    @Column(length = 1000)
    private String attachmentUrls;

    @Transient
    private Long readCount;

    @Transient
    private Long unreadCount;

    @Transient
    private Boolean read;

    @Transient
    private List<AttachmentResponse> attachments = new ArrayList<>();

    @Transient
    public String getStatusLabel() {
        return OperationDict.announcementStatusLabel(status);
    }

    @Transient
    public String getTypeLabel() {
        return OperationDict.announcementTypeLabel(type);
    }

    @Transient
    public String getTargetTypeLabel() {
        return OperationDict.announcementTargetLabel(targetType);
    }
}
