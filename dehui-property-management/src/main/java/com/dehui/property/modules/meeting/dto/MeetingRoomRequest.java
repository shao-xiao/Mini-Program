package com.dehui.property.modules.meeting.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MeetingRoomRequest {
    private String name;

    private String roomName;

    @NotBlank(message = "位置不能为空")
    private String location;

    @Min(value = 1, message = "容纳人数必须大于0")
    private Integer capacity;

    private String equipment;

    private String facilities;

    @DecimalMin(value = "0", message = "工作时间费率不能为负数")
    private BigDecimal workdayHourlyRate;

    private BigDecimal workdayWorkHourRate;

    @DecimalMin(value = "0", message = "非工作时间费率不能为负数")
    private BigDecimal offHourHourlyRate;

    private BigDecimal workdayOffHourRate;

    @DecimalMin(value = "0", message = "节假日费率不能为负数")
    private BigDecimal holidayHourlyRate;

    private BigDecimal holidayRate;

    private String status;

    private String remark;
}
