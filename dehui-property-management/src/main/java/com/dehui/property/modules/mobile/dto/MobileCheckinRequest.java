package com.dehui.property.modules.mobile.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MobileCheckinRequest {
    private String checkinType;
    private String location;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String remark;
}
