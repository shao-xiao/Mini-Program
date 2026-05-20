package com.dehui.property.modules.parking.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ParkingOperationLogResponse {
    private Long id;
    private String targetType;
    private Long targetId;
    private String action;
    private String actionText;
    private String beforeJson;
    private String afterJson;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime createdTime;
}
