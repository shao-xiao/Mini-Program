package com.dehui.property.modules.contract.dto;

public record ContractSummaryResponse(
        long total,
        long draft,
        long pendingApproval,
        long active,
        long suspended,
        long expiringSoon,
        long terminated,
        long cancelled
) {
}
