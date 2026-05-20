package com.dehui.property.modules.energy.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EnergyReadingRequest {
    @NotNull(message = "表具不能为空")
    private Long meterId;

    @NotNull(message = "抄表日期不能为空")
    private LocalDate readingDate;

    private String periodMonth;

    @NotNull(message = "本次读数不能为空")
    private BigDecimal currentReading;

    @NotNull(message = "单价不能为空")
    private BigDecimal unitPrice;

    private Long operatorId;

    private String operatorName;

    private String remark;
}
