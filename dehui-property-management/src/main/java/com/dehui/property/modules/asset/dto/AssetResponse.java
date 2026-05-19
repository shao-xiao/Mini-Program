package com.dehui.property.modules.asset.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AssetResponse {
    private Long id;
    private String assetCode;
    private String assetName;
    private String assetCategory;
    private String assetType;
    private String manufacturer;
    private String model;
    private String serialNo;
    private Long buildingId;
    private String buildingName;
    private Long floorId;
    private String floorName;
    private Integer floorNumber;
    private Long roomId;
    private String roomName;
    private String roomNumber;
    private String locationDesc;
    private String locationText;
    private String status;
    private String statusText;
    private LocalDate installDate;
    private LocalDate warrantyStartDate;
    private LocalDate warrantyEndDate;
    private String responsiblePerson;
    private Integer maintenanceCycleDays;
    private LocalDate lastMaintenanceDate;
    private LocalDate nextMaintenanceDate;
    private String remark;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
