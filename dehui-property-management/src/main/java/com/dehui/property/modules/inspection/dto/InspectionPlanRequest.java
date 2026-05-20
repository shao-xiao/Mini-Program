package com.dehui.property.modules.inspection.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class InspectionPlanRequest {
    private String planName;
    private String inspectionType;
    private String area;
    private String target;
    private String inspector;
    private LocalDate plannedDate;
    private String status;
    private String remark;
}
