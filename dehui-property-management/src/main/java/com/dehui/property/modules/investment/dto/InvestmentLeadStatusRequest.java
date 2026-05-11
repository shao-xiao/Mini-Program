package com.dehui.property.modules.investment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InvestmentLeadStatusRequest {
    @NotBlank(message = "请选择线索状态")
    private String status;
}
