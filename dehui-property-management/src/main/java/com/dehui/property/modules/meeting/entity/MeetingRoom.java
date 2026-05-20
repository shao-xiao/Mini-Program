package com.dehui.property.modules.meeting.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "meeting_room")
@EqualsAndHashCode(callSuper = true)
public class MeetingRoom extends BaseEntity {

    @Column(nullable = false)
    private String roomName;

    private String location;

    private Integer capacity;

    private String facilities;

    private BigDecimal workdayWorkHourRate;

    private BigDecimal workdayOffHourRate;

    private BigDecimal holidayRate;

    private String status;

    private String remark;
}
