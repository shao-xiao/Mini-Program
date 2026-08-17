package com.dehui.property.modules.billing.domain.service;

import com.dehui.property.common.BusinessException;
import java.time.LocalDate;

public final class FeeRuleActivationPolicy {

    private FeeRuleActivationPolicy() {
    }

    public static void requirePublishable(LocalDate effectiveFrom, LocalDate effectiveTo, LocalDate today) {
        if (effectiveFrom == null) throw new BusinessException(400, "规则版本生效开始日不能为空");
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new BusinessException(400, "规则版本有效区间无效");
        }
        if (effectiveTo != null && effectiveTo.isBefore(today)) {
            throw new BusinessException(409, "已过期的规则版本不能生效");
        }
        // A future effectiveFrom is intentionally publishable. ACTIVE means the
        // immutable version is released; applicability is still date-scoped.
    }
}
