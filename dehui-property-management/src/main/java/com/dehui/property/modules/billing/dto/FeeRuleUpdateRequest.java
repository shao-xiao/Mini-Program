package com.dehui.property.modules.billing.dto;

import com.dehui.property.modules.billing.domain.model.BillingCycle;
import com.dehui.property.modules.billing.domain.model.CalculationType;
import com.dehui.property.modules.billing.domain.model.FeeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record FeeRuleUpdateRequest(
        @NotBlank @Size(max = 128) String ruleName,
        @NotNull FeeType feeType,
        @NotNull CalculationType calculationType,
        @NotNull BillingCycle billingCycle,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        @Positive @Digits(integer = 16, fraction = 2) BigDecimal fixedAmount,
        @Positive @Digits(integer = 12, fraction = 6) BigDecimal unitPrice,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @Size(max = 500) String description,
        @NotNull @PositiveOrZero Long version
) {
}
