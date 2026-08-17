package com.dehui.property.modules.billing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record BillingPlanItemRequest(
        @NotNull @Positive Long feeRuleVersionId,
        @Size(max = 128) String itemName,
        @Positive @Digits(integer = 14, fraction = 4) BigDecimal quantity,
        @Positive @Digits(integer = 12, fraction = 6) BigDecimal unitPrice,
        @Positive @Digits(integer = 16, fraction = 2) BigDecimal fixedAmount,
        @PositiveOrZero Integer sortOrder
) {
}
