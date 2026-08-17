package com.dehui.property.modules.lease.dto;

public record LeaseSummaryResponse(
        long total,
        long pendingCheckin,
        long partiallyActive,
        long active,
        long partiallyTerminated,
        long terminated
) {
}
