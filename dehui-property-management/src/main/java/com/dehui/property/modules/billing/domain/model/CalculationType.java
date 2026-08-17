package com.dehui.property.modules.billing.domain.model;

public enum CalculationType {
    FIXED(true),
    AREA_UNIT_PRICE(true),
    QUANTITY_UNIT_PRICE(true),
    ONE_TIME(true),
    PERCENTAGE(false),
    LATE_FEE(false),
    FORMULA(false);

    private final boolean enabled;

    CalculationType(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
