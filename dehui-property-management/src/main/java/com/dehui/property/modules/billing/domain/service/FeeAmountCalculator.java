package com.dehui.property.modules.billing.domain.service;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.billing.domain.model.CalculationType;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class FeeAmountCalculator {

    private static final int MONEY_SCALE = 2;
    private static final int QUANTITY_SCALE = 4;

    private FeeAmountCalculator() {
    }

    public static BigDecimal calculate(
            CalculationType calculationType,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal fixedAmount
    ) {
        requireEnabled(calculationType);
        BigDecimal amount = switch (calculationType) {
            case FIXED, ONE_TIME -> positive(fixedAmount, "fixedAmount");
            case AREA_UNIT_PRICE, QUANTITY_UNIT_PRICE -> positive(quantity, "quantity")
                    .setScale(QUANTITY_SCALE, RoundingMode.HALF_UP)
                    .multiply(positive(unitPrice, "unitPrice"));
            case PERCENTAGE, LATE_FEE, FORMULA -> throw unsupported(calculationType);
        };
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public static void requireEnabled(CalculationType calculationType) {
        if (calculationType == null) {
            throw new BusinessException(400, "计算类型不能为空");
        }
        if (!calculationType.isEnabled()) {
            throw unsupported(calculationType);
        }
    }

    private static BigDecimal positive(BigDecimal value, String field) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, field + " 必须大于 0");
        }
        return value;
    }

    private static BusinessException unsupported(CalculationType type) {
        return new BusinessException(400, "计算类型 " + type + " 已预留但当前未启用");
    }
}
