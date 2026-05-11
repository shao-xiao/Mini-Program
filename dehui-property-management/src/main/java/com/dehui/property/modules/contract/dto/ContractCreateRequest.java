package com.dehui.property.modules.contract.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ContractCreateRequest {
    @NotBlank(message = "合同编号不能为空")
    private String contractNumber;

    private String contractName;

    @NotNull(message = "租户ID不能为空")
    private Long tenantId;

    @NotNull(message = "房间ID不能为空")
    private Long roomId;

    @NotNull(message = "租约ID不能为空")
    private Long leaseId;

    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    @NotNull(message = "月租金额不能为空")
    private BigDecimal rentAmount;

    private BigDecimal propertyFeeAmount;

    private BigDecimal depositAmount;

    private String paymentCycle;

    private Integer billingDay;

    private Integer dueDay;

    private String paymentTerms;

    private String remark;
}
