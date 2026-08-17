package com.dehui.property.modules.lease.domain.model;

public enum LeaseStatus {
    PENDING_CHECKIN,
    PARTIALLY_ACTIVE,
    ACTIVE,
    RENEWING,
    CHECKOUT_PENDING,
    PARTIALLY_TERMINATED,
    TERMINATED,
    CANCELLED,
    EXPIRED
}
