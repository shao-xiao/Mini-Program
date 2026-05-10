package com.dehui.property.modules.mobile.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MobileWorkOrderRequest {
    @NotBlank(message = "报修标题不能为空")
    private String title;

    @NotBlank(message = "问题描述不能为空")
    private String description;

    @NotBlank(message = "报修位置不能为空")
    private String location;

    @NotBlank(message = "报修类别不能为空")
    private String category;

    private String priority;

    private String contactPhone;
}
