package com.dehui.property.modules.asset.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AssetStatusRequest {
    @NotBlank(message = "资产状态不能为空")
    private String status;

    private String operator;

    private String description;
}
