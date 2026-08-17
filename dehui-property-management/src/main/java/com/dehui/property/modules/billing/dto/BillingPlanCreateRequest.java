package com.dehui.property.modules.billing.dto;

import com.dehui.property.modules.billing.domain.model.BillingCycle;
import com.dehui.property.modules.billing.domain.model.DueRule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record BillingPlanCreateRequest(
        @NotBlank @Size(max = 64) String planNo,
        @NotBlank @Size(max = 128) String planName,
        @NotNull @Positive Long tenantId,
        @NotNull @Positive Long contractId,
        @Positive Long leaseId,
        @NotNull LocalDate effectiveFrom,
        @NotNull LocalDate effectiveTo,
        @NotNull BillingCycle billingCycle,
        @NotNull @Min(0) @Max(365) Integer billingLeadDays,
        @NotNull DueRule dueRule,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotEmpty List<@Valid BillingPlanItemRequest> items
) {
}
