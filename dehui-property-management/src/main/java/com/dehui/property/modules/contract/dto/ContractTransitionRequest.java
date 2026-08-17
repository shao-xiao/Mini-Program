package com.dehui.property.modules.contract.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ContractTransitionRequest(
        @PositiveOrZero Long version,
        @Size(max = 500) String reason
) {
    public static ContractTransitionRequest empty() {
        return new ContractTransitionRequest(null, null);
    }
}
