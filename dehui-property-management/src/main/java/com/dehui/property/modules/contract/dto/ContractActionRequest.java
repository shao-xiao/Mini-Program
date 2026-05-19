package com.dehui.property.modules.contract.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ContractActionRequest {
    private LocalDate actionDate;
    private String operatorName;
    private Long operatorId;
    private String reason;
    private String remark;
}
