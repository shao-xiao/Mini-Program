package com.dehui.property.modules.inspection.entity;

import com.dehui.property.common.OperationDict;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "operation_inspection_record")
public class InspectionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate inspectionDate;

    private String inspector;

    private String inspectionType;

    private String area;

    private String target;

    private String result;

    private String problemDescription;

    private String actionTaken;

    private String status;

    private String remark;

    private Long planId;

    private Long convertedWorkOrderId;

    private LocalDateTime closedTime;

    private LocalDateTime deletedAt;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdTime = now;
        this.updatedTime = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedTime = LocalDateTime.now();
    }

    @Transient
    public String getStatusLabel() {
        return OperationDict.inspectionStatusLabel(status);
    }

    @Transient
    public String getResultLabel() {
        return OperationDict.inspectionResultLabel(result);
    }

    @Transient
    public String getInspectionTypeLabel() {
        return OperationDict.inspectionTypeLabel(inspectionType);
    }
}
