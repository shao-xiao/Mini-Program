package com.dehui.property.modules.billing.dto;

import com.dehui.property.modules.billing.domain.model.BillingCycle;
import com.dehui.property.modules.billing.domain.model.BillingPlanStatus;
import com.dehui.property.modules.billing.domain.model.DueRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record BillingPlanResponse(
        Long id,
        String planNo,
        String planName,
        Long tenantId,
        String tenantName,
        Long contractId,
        String contractNo,
        Long leaseId,
        String leaseNo,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        BillingCycle billingCycle,
        Integer billingLeadDays,
        DueRule dueRule,
        String currency,
        BillingPlanStatus status,
        Long itemCount,
        BigDecimal estimatedPeriodAmount,
        Long version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<BillingPlanItemResponse> items,
        Map<String, Object> customFields
) {
    public BillingPlanResponse(
            Long id,
            String planNo,
            String planName,
            Long tenantId,
            String tenantName,
            Long contractId,
            String contractNo,
            Long leaseId,
            String leaseNo,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            BillingCycle billingCycle,
            Integer billingLeadDays,
            DueRule dueRule,
            String currency,
            BillingPlanStatus status,
            Long itemCount,
            BigDecimal estimatedPeriodAmount,
            Long version,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<BillingPlanItemResponse> items
    ) {
        this(id, planNo, planName, tenantId, tenantName, contractId, contractNo, leaseId, leaseNo,
                effectiveFrom, effectiveTo, billingCycle, billingLeadDays, dueRule, currency, status,
                itemCount, estimatedPeriodAmount, version, createdAt, updatedAt, items, Map.of());
    }

    public BillingPlanResponse withCustomFields(Map<String, Object> values) {
        return new BillingPlanResponse(id, planNo, planName, tenantId, tenantName, contractId,
                contractNo, leaseId, leaseNo, effectiveFrom, effectiveTo, billingCycle,
                billingLeadDays, dueRule, currency, status, itemCount, estimatedPeriodAmount,
                version, createdAt, updatedAt, items, values == null ? Map.of() : Map.copyOf(values));
    }
}
