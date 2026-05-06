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
    private Long handlerId;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}