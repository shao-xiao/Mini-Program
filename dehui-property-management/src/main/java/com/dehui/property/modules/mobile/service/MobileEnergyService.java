package com.dehui.property.modules.mobile.service;

import com.dehui.property.common.Result;
import com.dehui.property.modules.energy.dto.EnergyBillResponse;
import com.dehui.property.modules.energy.dto.EnergyReadingResponse;
import com.dehui.property.modules.energy.dto.EnergyStatsResponse;
import com.dehui.property.modules.energy.service.EnergyService;
import com.dehui.property.modules.mobile.dto.MobileUserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MobileEnergyService {
    private final MobileAuthService mobileAuthService;
    private final EnergyService energyService;

    public Result<List<EnergyReadingResponse>> readings(String token, String meterType, String periodMonth) {
        MobileUserProfile profile = requireTenantProfile(token);
        if (profile == null) {
            return Result.error(403, "请先绑定租户身份后查看能耗");
        }
        return energyService.mobileReadings(profile.getBoundTenantId(), meterType, periodMonth);
    }

    public Result<List<EnergyBillResponse>> bills(String token) {
        MobileUserProfile profile = requireTenantProfile(token);
        if (profile == null) {
            return Result.error(403, "请先绑定租户身份后查看能源账单");
        }
        return energyService.mobileBills(profile.getBoundTenantId());
    }

    public Result<EnergyStatsResponse> stats(String token, String meterType, String periodMonth) {
        MobileUserProfile profile = requireTenantProfile(token);
        if (profile == null) {
            return Result.error(403, "请先绑定租户身份后查看能耗统计");
        }
        return energyService.stats(meterType, periodMonth, null, null, null,
                profile.getBoundTenantId(), null, null);
    }

    private MobileUserProfile requireTenantProfile(String token) {
        MobileUserProfile profile = mobileAuthService.getProfile(token);
        if (profile == null || !"TENANT".equals(profile.getUserType()) || profile.getBoundTenantId() == null) {
            return null;
        }
        return profile;
    }
}
