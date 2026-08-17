package com.dehui.property.modules.billing.domain.service;

import com.dehui.property.common.BusinessException;
import java.time.LocalDate;

public final class BillingPlanPolicy {

    private BillingPlanPolicy() {
    }

    public static void requireVersion(long expected, long actual) {
        if (expected != actual) {
            throw new BusinessException(409, "计费计划已被其他操作更新，请刷新后重试");
        }
    }

    public static void requireInside(
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            LocalDate boundaryFrom,
            LocalDate boundaryTo,
            String boundaryName
    ) {
        if (effectiveFrom == null || effectiveTo == null || effectiveTo.isBefore(effectiveFrom)) {
            throw new BusinessException(400, "计费计划有效区间无效");
        }
        if (effectiveFrom.isBefore(boundaryFrom) || effectiveTo.isAfter(boundaryTo)) {
            throw new BusinessException(400, "计费计划有效区间必须位于" + boundaryName + "有效期内");
        }
    }
}
