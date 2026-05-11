package com.dehui.property.modules.workorder.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class WorkOrderCompleteRequest {
    private String handlingResult;
    private Boolean billable;
    private BigDecimal chargeAmount;
    private String chargeRemark;
}
