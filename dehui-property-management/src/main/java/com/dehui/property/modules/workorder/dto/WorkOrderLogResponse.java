package com.dehui.property.modules.workorder.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkOrderLogResponse {
    private Long id;
    private Long workOrderId;
    private String operationType;
    private String oldStatus;
    private String oldStatusLabel;
    private String newStatus;
    private String newStatusLabel;
    private String operator;
    private String content;
    private LocalDateTime createdTime;
}
