package com.dehui.property.modules.contract.domain.model;

public enum ContractStatus {
    DRAFT,
    PENDING_APPROVAL,
    ACTIVE,
    SUSPENDED,
    TERMINATED,
    CANCELLED,
    EXPIRED;

    public static ContractStatus fromDatabase(String value) {
        if ("PENDING".equalsIgnoreCase(value)) {
            return PENDING_APPROVAL;
        }
        return valueOf(value.toUpperCase(java.util.Locale.ROOT));
    }
}
