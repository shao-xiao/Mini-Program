package com.dehui.property.modules.asset.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AssetMaintenanceRequest {
    private LocalDate maintenanceDate;

    private LocalDate nextMaintenanceDate;

    private String operator;

    private String description;

    private String attachmentUrl;
}
