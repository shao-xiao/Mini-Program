package com.dehui.property.modules.inspection.entity;

import com.dehui.property.common.BaseEntity;
import com.dehui.property.common.OperationDict;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "inspection_plan")
@EqualsAndHashCode(callSuper = true)
public class InspectionPlan extends BaseEntity {
    private String planName;
    private String inspectionType;
    private String area;
    private String target;
    private String inspector;
    private LocalDate plannedDate;
    private String status;

    @Column(length = 1000)
    private String remark;

    private LocalDateTime deletedAt;

    @Transient
    public String getInspectionTypeLabel() {
        return OperationDict.inspectionTypeLabel(inspectionType);
    }

    @Transient
    public String getStatusLabel() {
        return switch (status == null ? "" : status) {
            case "IN_PROGRESS" -> "进行中";
            case "COMPLETED" -> "已完成";
            case "CLOSED" -> "已关闭";
            default -> "未开始";
        };
    }
}
