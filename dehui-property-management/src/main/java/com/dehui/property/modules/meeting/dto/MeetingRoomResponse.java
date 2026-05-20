package com.dehui.property.modules.meeting.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MeetingRoomResponse {
    private Long id;
    private String name;
    private String roomName;
    private String location;
    private Integer capacity;
    private String equipment;
    private String facilities;
    private BigDecimal workdayHourlyRate;
    private BigDecimal workdayWorkHourRate;
    private BigDecimal offHourHourlyRate;
    private BigDecimal workdayOffHourRate;
    private BigDecimal holidayHourlyRate;
    private BigDecimal holidayRate;
    private String status;
    private String statusText;
    private String remark;
    private Boolean available;
    private String unavailableReason;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
