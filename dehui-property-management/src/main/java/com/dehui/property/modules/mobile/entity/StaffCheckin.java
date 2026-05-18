package com.dehui.property.modules.mobile.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "staff_checkin")
@EqualsAndHashCode(callSuper = true)
public class StaffCheckin extends BaseEntity {

    private Long sysUserId;

    private LocalDateTime checkinTime;

    private String checkinType;

    private String location;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String remark;

    private String status;
}
