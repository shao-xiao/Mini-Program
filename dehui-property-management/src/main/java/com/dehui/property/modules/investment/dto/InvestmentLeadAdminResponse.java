package com.dehui.property.modules.investment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class InvestmentLeadAdminResponse {
    private Long id;
    private String name;
    private String phone;
    private String companyName;
    private Double desiredArea;
    private String intendedUse;
    private String preferredVisitTime;
    private Long roomId;
    private String roomNumber;
    private String source;
    private String status;
    private String remark;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
