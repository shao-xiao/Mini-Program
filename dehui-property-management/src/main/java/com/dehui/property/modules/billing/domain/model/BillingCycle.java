package com.dehui.property.modules.billing.domain.model;

public enum BillingCycle {
    MONTHLY(1),
    QUARTERLY(3),
    SEMI_ANNUAL(6),
    ANNUAL(12),
    ONE_TIME(0);

    private final int periodMonths;

    BillingCycle(int periodMonths) {
        this.periodMonths = periodMonths;
    }

    public int periodMonths() {
        return periodMonths;
    }

    public boolean recurring() {
        return periodMonths > 0;
    }
}
