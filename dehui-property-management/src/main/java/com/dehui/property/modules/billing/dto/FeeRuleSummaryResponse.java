package com.dehui.property.modules.billing.dto;

public record FeeRuleSummaryResponse(
        long totalCount,
        long draftCount,
        long activeCount,
        long disabledCount
) {
}
