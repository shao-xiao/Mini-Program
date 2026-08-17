package com.dehui.property.modules.billing.domain.service;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.billing.domain.model.BillingCycle;
import com.dehui.property.modules.billing.domain.model.CalculationType;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class FeeRulePolicy {

    private FeeRulePolicy() {
    }

    public static void validate(
            CalculationType calculationType,
            BillingCycle billingCycle,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            BigDecimal fixedAmount,
            BigDecimal unitPrice
    ) {
        FeeAmountCalculator.requireEnabled(calculationType);
        if (billingCycle == null) throw new BusinessException(400, "计费周期不能为空");
        if (effectiveFrom == null) throw new BusinessException(400, "生效开始日期不能为空");
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new BusinessException(400, "生效结束日期不能早于开始日期");
        }
        boolean oneTimeCalculation = calculationType == CalculationType.ONE_TIME;
        boolean oneTimeCycle = billingCycle == BillingCycle.ONE_TIME;
        if (oneTimeCalculation != oneTimeCycle) {
            throw new BusinessException(400, "ONE_TIME 计算类型必须且只能使用 ONE_TIME 周期");
        }
        if (calculationType == CalculationType.FIXED || oneTimeCalculation) {
            if (unitPrice != null) {
                throw new BusinessException(400, "固定金额规则不能同时提交 unitPrice");
            }
            FeeAmountCalculator.calculate(calculationType, null, null, fixedAmount);
        } else {
            if (fixedAmount != null) {
                throw new BusinessException(400, "单位计价规则不能同时提交 fixedAmount");
            }
            if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(400, "面积或数量计价的 unitPrice 必须大于 0");
            }
        }
    }
}
