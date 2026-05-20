package com.dehui.property.modules.parking.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "parking_space")
@EqualsAndHashCode(callSuper = true)
public class ParkingSpace extends BaseEntity {

    private String spaceCode;     // 车位编号，如 B1-001

    private String area;          // 区域，如 B1、B2、地面停车区

    private String spaceType;     // FIXED / TEMP / VIP

    private String status;        // AVAILABLE / OCCUPIED / DISABLED

    private String floor;

    private Integer sortOrder;

    private Long tenantId;        // 绑定租户，可为空

    private String plateNumber;   // 绑定车牌，可为空

    private String remark;

    private LocalDateTime deletedAt;
}
