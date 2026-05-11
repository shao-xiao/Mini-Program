package com.dehui.property.modules.workorder.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

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
    private Boolean slaOverdue;
    private String slaLabel;
    private Long reporterId;
    private Long mobileUserId;
    private Long tenantId;
    private String reporterName;
    private String reporterPhone;
    private List<String> imageUrls;
    private String handlingResult;
    private Integer rating;
    private String evaluationContent;
    private LocalDateTime evaluationTime;
    private Long handlerId;
    private LocalDateTime submittedTime;
    private LocalDateTime assignedTime;
    private LocalDateTime processingTime;
    private LocalDateTime completedTime;
    private LocalDateTime closedTime;
    private LocalDateTime cancelledTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
