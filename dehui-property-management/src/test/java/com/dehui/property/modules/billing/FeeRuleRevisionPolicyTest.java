package com.dehui.property.modules.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.billing.domain.service.BillingPlanPolicy;
import com.dehui.property.modules.billing.domain.service.FeeRuleRevisionPolicy;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FeeRuleRevisionPolicyTest {

    @Test
    void revisionCreatesANewMonotonicVersionNumber() {
        int immutableCurrentVersion = 7;
        assertEquals(8, FeeRuleRevisionPolicy.nextVersionNo(immutableCurrentVersion));
        assertEquals(7, immutableCurrentVersion, "creating a revision must not overwrite its predecessor");
    }

    @Test
    void aggregateOptimisticVersionsRejectStaleFeeRuleAndPlanWrites() {
        BusinessException feeRuleConflict = assertThrows(BusinessException.class,
                () -> FeeRuleRevisionPolicy.requireVersion(3, 4));
        assertEquals(409, feeRuleConflict.getCode());

        BusinessException planConflict = assertThrows(BusinessException.class,
                () -> BillingPlanPolicy.requireVersion(9, 10));
        assertEquals(409, planConflict.getCode());
    }

    @Test
    void revisionEffectiveStartMustAdvancePastPublishedVersion() {
        LocalDate activeStart = LocalDate.of(2026, 1, 1);
        assertThrows(BusinessException.class,
                () -> FeeRuleRevisionPolicy.requireRevisionAfter(activeStart, activeStart));
        assertEquals(2, FeeRuleRevisionPolicy.nextVersionNo(1));
    }
}
