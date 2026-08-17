package com.dehui.property.modules.billing.domain.service;

import com.dehui.property.common.BusinessException;
import java.time.LocalDate;

public final class FeeRuleRevisionPolicy {

    private FeeRuleRevisionPolicy() {
    }

    public static void requireVersion(long expected, long actual) {
        if (expected != actual) {
            throw new BusinessException(409, "收费规则已被其他操作更新，请刷新后重试");
        }
    }

    public static int nextVersionNo(int currentVersionNo) {
        if (currentVersionNo < 1) throw new BusinessException(409, "收费规则版本号无效");
        return Math.addExact(currentVersionNo, 1);
    }

    public static void requireRevisionAfter(LocalDate activeEffectiveFrom, LocalDate revisionEffectiveFrom) {
        if (activeEffectiveFrom != null && !revisionEffectiveFrom.isAfter(activeEffectiveFrom)) {
            throw new BusinessException(409, "新版本生效开始日必须晚于当前生效版本");
        }
    }
}
