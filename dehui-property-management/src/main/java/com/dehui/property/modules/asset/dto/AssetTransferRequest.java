package com.dehui.property.modules.asset.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssetTransferRequest {
    @NotNull(message = "楼宇不能为空")
    private Long buildingId;

    @NotNull(message = "楼层不能为空")
    private Long floorId;

    private Long roomId;

    private String locationDesc;

    private String operator;

    private String description;
}
