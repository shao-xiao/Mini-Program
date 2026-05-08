package com.dehui.property.modules.meeting.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MeetingRoomRequest {
    @NotBlank(message = "会议室名称不能为空")
    private String roomName;

    private String location;

    private Integer capacity;

    private String facilities;

    private BigDecimal workdayWorkHourRate;

    private BigDecimal workdayOffHourRate;

    private BigDecimal holidayRate;

    private String status;
}
