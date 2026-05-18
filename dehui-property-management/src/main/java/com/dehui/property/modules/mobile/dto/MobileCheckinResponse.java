package com.dehui.property.modules.mobile.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MobileCheckinResponse {
    private Long id;
    private Long sysUserId;
    private String sysUserName;
    private String sysRealName;
    private LocalDateTime checkinTime;
    private String checkinType;
    private String checkinTypeText;
    private String location;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String remark;
    private String status;
    private LocalDateTime createdTime;
}
