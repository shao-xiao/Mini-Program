package com.dehui.property.modules.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.billing.domain.model.CalculationType;
import com.dehui.property.modules.billing.domain.service.FeeAmountCalculator;
import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class FeeAmountCalculatorTest {

    @Test
    void calculatesTheFourEnabledStrategiesAtMoneyScale() {
        assertEquals(new BigDecimal("88.10"), calculate(CalculationType.FIXED, null, null, "88.10"));
        assertEquals(new BigDecimal("23.75"), calculate(CalculationType.AREA_UNIT_PRICE, "10.125", "2.345678", null));
        assertEquals(new BigDecimal("3.33"), calculate(CalculationType.QUANTITY_UNIT_PRICE, "3", "1.11", null));
        assertEquals(new BigDecimal("1200.00"), calculate(CalculationType.ONE_TIME, null, null, "1200"));
    }

    @ParameterizedTest
    @MethodSource("disabledTypes")
    void rejectsReservedCalculationTypes(CalculationType type) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> FeeAmountCalculator.calculate(type, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));
        assertEquals(400, exception.getCode());
    }

    private static Stream<CalculationType> disabledTypes() {
        return Stream.of(CalculationType.PERCENTAGE, CalculationType.LATE_FEE, CalculationType.FORMULA);
    }

    private BigDecimal calculate(CalculationType type, String quantity, String unitPrice, String fixedAmount) {
        return FeeAmountCalculator.calculate(type, decimal(quantity), decimal(unitPrice), decimal(fixedAmount));
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
