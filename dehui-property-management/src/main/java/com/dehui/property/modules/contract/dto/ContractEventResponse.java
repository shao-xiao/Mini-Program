package com.dehui.property.modules.contract.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ContractEventResponse {
    private Long id;
    private Long contractId;
    private String action;
    private String actionText;
    private String beforeStatus;
    private String afterStatus;
    private Long operatorId;
    private String operatorName;
    private String remark;
    private LocalDateTime createdTime;
}
