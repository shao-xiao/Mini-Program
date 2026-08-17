package com.dehui.property.modules.lease.domain.service;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.lease.domain.model.LeaseStatus;

public final class LeaseStatusDeriver {

    private LeaseStatusDeriver() {
    }

    public static LeaseStatus derive(int totalRooms, int activeRooms, int terminatedRooms) {
        if (totalRooms <= 0 || activeRooms < 0 || terminatedRooms < 0
                || activeRooms + terminatedRooms > totalRooms) {
            throw new BusinessException(409, "租约房间状态计数不一致");
        }
        if (terminatedRooms == totalRooms) {
            return LeaseStatus.TERMINATED;
        }
        if (terminatedRooms > 0) {
            return LeaseStatus.PARTIALLY_TERMINATED;
        }
        if (activeRooms == totalRooms) {
            return LeaseStatus.ACTIVE;
        }
        if (activeRooms > 0) {
            return LeaseStatus.PARTIALLY_ACTIVE;
        }
        return LeaseStatus.PENDING_CHECKIN;
    }
}
