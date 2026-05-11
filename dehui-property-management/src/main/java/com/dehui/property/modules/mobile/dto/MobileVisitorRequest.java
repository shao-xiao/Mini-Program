package com.dehui.property.modules.mobile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MobileVisitorRequest {
    @NotBlank(message = "请填写访客姓名")
    private String visitorName;

    @NotBlank(message = "请填写访客手机号")
    private String visitorPhone;

    private Long tenantId;

    @NotBlank(message = "请填写被访人")
    private String visitedPerson;

    @NotBlank(message = "请填写来访事由")
    private String visitReason;

    @NotNull(message = "请选择来访时间")
    private LocalDateTime visitTime;

    private String carPlateNo;

    private String remark;
}
