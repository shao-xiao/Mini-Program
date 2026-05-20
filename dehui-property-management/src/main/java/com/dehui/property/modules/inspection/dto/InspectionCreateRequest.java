package com.dehui.property.modules.inspection.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class InspectionCreateRequest {

    private LocalDate inspectionDate;
    private String inspector;
    private String inspectionType;
    private String area;
    private String target;
    private String result;
    private String problemDescription;
    private String actionTaken;
    private String remark;
    private Long planId;
    private String status;
}
