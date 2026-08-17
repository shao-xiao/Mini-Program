package com.dehui.property.modules.billing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record FeeRuleActionRequest(
        @NotNull @PositiveOrZero Long version,
        @Size(max = 500) String reason
) {
}
