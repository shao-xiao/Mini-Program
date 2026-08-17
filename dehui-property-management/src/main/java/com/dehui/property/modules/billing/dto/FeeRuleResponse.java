package com.dehui.property.modules.billing.dto;

import com.dehui.property.modules.billing.domain.model.BillingCycle;
import com.dehui.property.modules.billing.domain.model.CalculationType;
import com.dehui.property.modules.billing.domain.model.FeeRuleStatus;
import com.dehui.property.modules.billing.domain.model.FeeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public record FeeRuleResponse(
        Long id,
        String ruleCode,
        String ruleName,
        FeeType feeType,
        FeeRuleStatus status,
        Long currentVersionId,
        Integer currentVersionNo,
        Long activeVersionId,
        Integer activeVersionNo,
        CalculationType calculationType,
        BillingCycle billingCycle,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        BigDecimal fixedAmount,
        BigDecimal unitPrice,
        String currency,
        String description,
        Long version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Map<String, Object> customFields
) {
    public FeeRuleResponse(
            Long id,
            String ruleCode,
            String ruleName,
            FeeType feeType,
            FeeRuleStatus status,
            Long currentVersionId,
            Integer currentVersionNo,
            Long activeVersionId,
            Integer activeVersionNo,
            CalculationType calculationType,
            BillingCycle billingCycle,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            BigDecimal fixedAmount,
            BigDecimal unitPrice,
            String currency,
            String description,
            Long version,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this(id, ruleCode, ruleName, feeType, status, currentVersionId, currentVersionNo,
                activeVersionId, activeVersionNo, calculationType, billingCycle, effectiveFrom,
                effectiveTo, fixedAmount, unitPrice, currency, description, version, createdAt,
                updatedAt, Map.of());
    }

    public FeeRuleResponse withCustomFields(Map<String, Object> values) {
        return new FeeRuleResponse(id, ruleCode, ruleName, feeType, status, currentVersionId,
                currentVersionNo, activeVersionId, activeVersionNo, calculationType, billingCycle,
                effectiveFrom, effectiveTo, fixedAmount, unitPrice, currency, description, version,
                createdAt, updatedAt, values == null ? Map.of() : Map.copyOf(values));
    }
}
