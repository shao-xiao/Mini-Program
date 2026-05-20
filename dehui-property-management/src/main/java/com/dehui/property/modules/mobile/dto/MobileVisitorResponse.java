package com.dehui.property.modules.mobile.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MobileVisitorResponse {
    private Long id;
    private String visitorName;
    private String visitorPhone;
    private Long tenantId;
    private String tenantName;
    private String visitedPerson;
    private String visitReason;
    private LocalDateTime visitTime;
    private LocalDateTime enterTime;
    private LocalDateTime leaveTime;
    private String status;
    private String statusText;
    private String source;
    private String carPlateNo;
    private String remark;
    private String rejectReason;
    private LocalDateTime createdTime;
}
