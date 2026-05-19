package com.dehui.property.modules.asset.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AssetOperationLogResponse {
    private Long id;
    private Long assetId;
    private String operationType;
    private String operationTypeText;
    private String oldStatus;
    private String newStatus;
    private String oldLocation;
    private String newLocation;
    private String operator;
    private LocalDateTime operationTime;
    private String description;
    private String attachmentUrl;
    private LocalDateTime createdTime;
}
