package com.dehui.property.modules.workorder.dto;

import lombok.Data;

@Data
public class WorkOrderStatusRequest {
    private String status;
    private String operator;
    private String remark;
}
