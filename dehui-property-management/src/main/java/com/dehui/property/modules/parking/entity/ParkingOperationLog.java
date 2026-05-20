package com.dehui.property.modules.parking.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Entity
@Table(name = "parking_operation_log")
@EqualsAndHashCode(callSuper = true)
public class ParkingOperationLog extends BaseEntity {
    private String targetType;

    private Long targetId;

    private String action;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String beforeJson;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String afterJson;

    private Long operatorId;

    private String operatorName;
}
