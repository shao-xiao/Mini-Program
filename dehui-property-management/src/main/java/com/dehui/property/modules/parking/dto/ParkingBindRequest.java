package com.dehui.property.modules.parking.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ParkingBindRequest {
    private String partyType;
    private Long partyId;
    private Long tenantId;
    private String partyName;
    private String partyNameSnapshot;
    private String plateNo;
    private String plateNumber;
    private LocalDate startDate;
    private String billingType;
    private BigDecimal monthlyFee;
    private String operatorName;
    private String remark;
}
