package com.dehui.property.modules.billing.dto;

import com.dehui.property.modules.billing.domain.model.BillingCycle;
import com.dehui.property.modules.billing.domain.model.FeeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record FeeRuleCompatibilityRequest(
        @NotBlank @Size(max = 128) String ruleName,
        Long tenantId,
        Long contractId,
        @NotNull FeeType feeType,
        @NotNull @Positive BigDecimal amount,
        @NotNull BillingCycle cycle,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        Integer generateDay,
        @Size(max = 500) String remark
) {
}
