package com.dehui.property.modules.billing.dto;

import java.math.BigDecimal;

public record BillingPlanSummaryResponse(
        long totalCount,
        long draftCount,
        long activeCount,
        long terminatedCount,
        BigDecimal estimatedPeriodAmount
) {
}
