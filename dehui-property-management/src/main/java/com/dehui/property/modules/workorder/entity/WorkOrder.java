package com.dehui.property.modules.workorder.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "work_order")
@EqualsAndHashCode(callSuper = true)
public class WorkOrder extends BaseEntity {

    private String orderNumber;

    private String title;

    private String description;

    private Long equipmentId;

    private String location;

    /**
     * 工单类型：
     * REPAIR   维修
     * PATROL   巡更
     * CLEAN    保洁
     * SECURITY 安保
     */
    private String orderType;

    private String category;

    private String priority;

    private String status;

    private Long reporterId;

    private Long mobileUserId;

    private Long tenantId;

    private String reporterName;

    private String reporterPhone;

    private Long handlerId;

    private LocalDateTime submittedTime;

    private LocalDateTime assignedTime;

    private LocalDateTime processingTime;

    private LocalDateTime completedTime;

    private LocalDateTime closedTime;

    private LocalDateTime cancelledTime;
}
