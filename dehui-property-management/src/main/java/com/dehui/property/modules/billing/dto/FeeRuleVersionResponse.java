package com.dehui.property.modules.billing.dto;

import com.dehui.property.modules.billing.domain.model.BillingCycle;
import com.dehui.property.modules.billing.domain.model.CalculationType;
import com.dehui.property.modules.billing.domain.model.FeeRuleVersionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FeeRuleVersionResponse(
        Long id,
        Long feeRuleId,
        Integer versionNo,
        FeeRuleVersionStatus status,
        CalculationType calculationType,
        BillingCycle billingCycle,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        BigDecimal fixedAmount,
        BigDecimal unitPrice,
        String currency,
        LocalDateTime activatedAt,
        LocalDateTime createdAt
) {
}
