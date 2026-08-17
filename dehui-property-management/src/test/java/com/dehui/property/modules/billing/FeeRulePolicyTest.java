package com.dehui.property.modules.billing;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.billing.domain.model.BillingCycle;
import com.dehui.property.modules.billing.domain.model.CalculationType;
import com.dehui.property.modules.billing.domain.service.FeeRuleActivationPolicy;
import com.dehui.property.modules.billing.domain.service.FeeRulePolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FeeRulePolicyTest {

    private static final LocalDate START = LocalDate.of(2026, 8, 1);

    @Test
    void oneTimeCalculationAndCycleMustBePaired() {
        BusinessException recurringOneTime = assertThrows(BusinessException.class, () -> FeeRulePolicy.validate(
                CalculationType.ONE_TIME, BillingCycle.MONTHLY, START, null,
                new BigDecimal("100"), null
        ));
        assertEquals(400, recurringOneTime.getCode());

        assertThrows(BusinessException.class, () -> FeeRulePolicy.validate(
                CalculationType.FIXED, BillingCycle.ONE_TIME, START, null,
                new BigDecimal("100"), null
        ));
        assertDoesNotThrow(() -> FeeRulePolicy.validate(
                CalculationType.ONE_TIME, BillingCycle.ONE_TIME, START, START,
                new BigDecimal("100"), null
        ));
    }

    @Test
    void futureVersionsCanBePublishedButExpiredVersionsCannot() {
        LocalDate today = LocalDate.of(2026, 7, 20);
        assertDoesNotThrow(() -> FeeRuleActivationPolicy.requirePublishable(
                today.plusMonths(1), today.plusMonths(2), today
        ));
        BusinessException expired = assertThrows(BusinessException.class,
                () -> FeeRuleActivationPolicy.requirePublishable(
                        today.minusMonths(2), today.minusDays(1), today
                ));
        assertEquals(409, expired.getCode());
    }

    @Test
    void pricingModelsRejectAmbiguousAmountInputs() {
        assertThrows(BusinessException.class, () -> FeeRulePolicy.validate(
                CalculationType.FIXED, BillingCycle.MONTHLY, START, null,
                new BigDecimal("100"), new BigDecimal("2")
        ));
        assertThrows(BusinessException.class, () -> FeeRulePolicy.validate(
                CalculationType.AREA_UNIT_PRICE, BillingCycle.MONTHLY, START, null,
                new BigDecimal("100"), new BigDecimal("2")
        ));
    }
}
