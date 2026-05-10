package com.dehui.property.modules.workorder.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class WorkOrderResponse {
    private Long id;
    private String orderNumber;
    private String title;
    private String description;
    private Long equipmentId;
    private String location;
    private String orderType;
    private String category;
    private String priority;
    private String status;
    private Long reporterId;
    private Long mobileUserId;
    private Long tenantId;
    private String reporterName;
    private String reporterPhone;
    private Long handlerId;
    private LocalDateTime submittedTime;
    private LocalDateTime assignedTime;
    private LocalDateTime processingTime;
    private LocalDateTime completedTime;
    private LocalDateTime closedTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
