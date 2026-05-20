package com.dehui.property.modules.workorder.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Entity
@Table(name = "work_order_log")
@EqualsAndHashCode(callSuper = true)
public class WorkOrderLog extends BaseEntity {
    private Long workOrderId;

    private String operationType;

    private String oldStatus;

    private String newStatus;

    private String operator;

    @Column(length = 1000)
    private String content;
}
