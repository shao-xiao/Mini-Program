package com.dehui.property.modules.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AssetRequest {
    @NotBlank(message = "资产编号不能为空")
    private String assetCode;

    @NotBlank(message = "资产名称不能为空")
    private String assetName;

    private String assetCategory;

    private String assetType;

    private String manufacturer;

    private String model;

    private String serialNo;

    @NotNull(message = "楼宇不能为空")
    private Long buildingId;

    @NotNull(message = "楼层不能为空")
    private Long floorId;

    private Long roomId;

    private String locationDesc;

    private String status;

    private LocalDate installDate;

    private LocalDate warrantyStartDate;

    private LocalDate warrantyEndDate;

    private String responsiblePerson;

    private Integer maintenanceCycleDays;

    private LocalDate lastMaintenanceDate;

    private LocalDate nextMaintenanceDate;

    private String remark;
}
