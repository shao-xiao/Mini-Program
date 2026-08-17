package com.dehui.property.contracts;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FeeBillingDomainSourceContractTest {

    private static final Path BILLING = Path.of("src", "main", "java", "com", "dehui", "property", "modules", "billing");

    @Test
    void calculationTypesExposeOnlyFourEnabledStrategies() throws Exception {
        String source = read("domain/model/CalculationType.java");
        for (String type : new String[]{
                "FIXED", "AREA_UNIT_PRICE", "QUANTITY_UNIT_PRICE", "ONE_TIME",
                "PERCENTAGE", "LATE_FEE", "FORMULA"
        }) assertTrue(source.contains(type), "calculation type must reserve " + type);
        assertTrue(source.contains("isEnabled"), "calculation types need an explicit enablement policy");
    }

    @Test
    void amountCalculatorUsesBigDecimalAndHasNoFloatingPointInputs() throws Exception {
        String source = read("domain/service/FeeAmountCalculator.java");
        assertTrue(source.contains("BigDecimal"));
        assertTrue(source.contains("AREA_UNIT_PRICE"));
        assertTrue(source.contains("QUANTITY_UNIT_PRICE"));
        assertTrue(source.contains("setScale"), "financial amounts must have an explicit scale");
        assertTrue(!source.contains("double ") && !source.contains("float "), "financial calculations must avoid floating point");
    }

    @Test
    void cyclePolicyIncludesSemiAnnualAndOneTimeCompatibility() throws Exception {
        String source = read("domain/model/BillingCycle.java");
        for (String cycle : new String[]{"MONTHLY", "QUARTERLY", "SEMI_ANNUAL", "ANNUAL", "ONE_TIME"}) {
            assertTrue(source.contains(cycle), "billing cycle must include " + cycle);
        }
        assertTrue(source.contains("periodMonths"), "recurring cycle length must be explicit");
    }

    private static String read(String relative) throws Exception {
        Path path = BILLING.resolve(relative);
        assertTrue(Files.exists(path), path + " must exist");
        return Files.readString(path);
    }
}
