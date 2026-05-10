package com.dehui.property.modules.mobile.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MobileWorkOrderResponse {
    private Long id;
    private String orderNumber;
    private String title;
    private String description;
    private String location;
    private String orderType;
    private String orderTypeText;
    private String category;
    private String categoryText;
    private String priority;
    private String priorityText;
    private String status;
    private String statusText;
    private String reporterName;
    private String reporterPhone;
    private LocalDateTime submittedTime;
    private LocalDateTime assignedTime;
    private LocalDateTime processingTime;
    private LocalDateTime completedTime;
    private LocalDateTime closedTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
