package com.dehui.property.modules.investment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InvestmentLeadRequest {
    @NotBlank(message = "请填写联系人")
    private String name;

    @NotBlank(message = "请填写联系电话")
    private String phone;

    private String companyName;
    private Double desiredArea;
    private String intendedUse;
    private String preferredVisitTime;
    private Long roomId;
    private String remark;
}
