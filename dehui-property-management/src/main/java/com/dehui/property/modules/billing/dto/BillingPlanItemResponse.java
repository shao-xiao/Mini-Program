package com.dehui.property.modules.billing.dto;

import com.dehui.property.modules.billing.domain.model.CalculationType;
import java.math.BigDecimal;

public record BillingPlanItemResponse(
        Long id,
        Long feeRuleVersionId,
        String ruleCode,
        String ruleName,
        CalculationType calculationType,
        String itemName,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal fixedAmount,
        BigDecimal amount,
        Integer sortOrder
) {
}
