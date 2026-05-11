package com.dehui.property.modules.mobile.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MobileWorkOrderEvaluationRequest {
    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分不能低于1分")
    @Max(value = 5, message = "评分不能超过5分")
    private Integer rating;

    private String content;
}
