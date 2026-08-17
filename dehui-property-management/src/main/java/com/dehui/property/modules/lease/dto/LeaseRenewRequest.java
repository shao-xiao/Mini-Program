package com.dehui.property.modules.lease.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record LeaseRenewRequest(
        @NotNull LocalDate plannedStartDate,
        @NotNull LocalDate plannedEndDate,
        @PositiveOrZero Long version,
        @Size(max = 500) String reason
) {
}
