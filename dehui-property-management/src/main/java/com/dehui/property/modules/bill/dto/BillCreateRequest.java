package com.dehui.property.modules.bill.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class BillCreateRequest {
    private String billNumber;

    @NotNull(message = "租户ID不能为空")
    private Long tenantId;

    private Long contractId;

    @NotBlank(message = "账单类型不能为空")
    private String billType;

    private String title;

    @NotNull(message = "账期开始日期不能为空")
    private LocalDate periodStart;

    @NotNull(message = "账期结束日期不能为空")
    private LocalDate periodEnd;

    @NotNull(message = "账单金额不能为空")
    @DecimalMin(value = "0.01", message = "账单金额必须大于0")
    private BigDecimal amount;

    @NotNull(message = "到期日期不能为空")
    private LocalDate dueDate;

    private String remark;
}
