package com.dehui.property.modules.mobile.service;

import com.dehui.property.common.Result;
import com.dehui.property.modules.mobile.dto.MobileCheckinHomeResponse;
import com.dehui.property.modules.mobile.dto.MobileCheckinRequest;
import com.dehui.property.modules.mobile.dto.MobileCheckinResponse;
import com.dehui.property.modules.mobile.dto.MobileUserProfile;
import com.dehui.property.modules.mobile.entity.StaffCheckin;
import com.dehui.property.modules.mobile.repository.StaffCheckinRepository;
import com.dehui.property.modules.system.entity.SysUser;
import com.dehui.property.modules.system.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MobileCheckinService {

    private final MobileAuthService mobileAuthService;
    private final StaffCheckinRepository staffCheckinRepository;
    private final SysUserRepository sysUserRepository;

    public Result<MobileCheckinHomeResponse> home(String token) {
        MobileUserProfile profile = mobileAuthService.getProfile(token);
        Result<MobileUserProfile> identityResult = requireInternalProfile(profile);
        if (identityResult.getCode() != 200) {
            return Result.error(identityResult.getCode(), identityResult.getMessage());
        }

        List<MobileCheckinResponse> checkins = staffCheckinRepository
                .findBySysUserIdOrderByCheckinTimeDesc(profile.getBoundSysUserId())
                .stream()
                .map(this::toResponse)
                .toList();
        return Result.success(new MobileCheckinHomeResponse(profile, checkins));
    }

    @Transactional
    public Result<MobileCheckinResponse> create(String token, MobileCheckinRequest request) {
        MobileCheckinRequest safeRequest = request == null ? new MobileCheckinRequest() : request;
        MobileUserProfile profile = mobileAuthService.getProfile(token);
        Result<MobileUserProfile> identityResult = requireInternalProfile(profile);
        if (identityResult.getCode() != 200) {
            return Result.error(identityResult.getCode(), identityResult.getMessage());
        }

        StaffCheckin checkin = new StaffCheckin();
        checkin.setSysUserId(profile.getBoundSysUserId());
        checkin.setCheckinTime(LocalDateTime.now());
        checkin.setCheckinType(normalizeType(safeRequest.getCheckinType()));
        checkin.setLocation(safeRequest.getLocation());
        checkin.setLongitude(safeRequest.getLongitude());
        checkin.setLatitude(safeRequest.getLatitude());
        checkin.setRemark(safeRequest.getRemark());
        checkin.setStatus("RECORDED");
        return Result.success(toResponse(staffCheckinRepository.save(checkin)));
    }

    private Result<MobileUserProfile> requireInternalProfile(MobileUserProfile profile) {
        if (profile == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        if (!"INTERNAL".equals(profile.getUserType()) || profile.getBoundSysUserId() == null) {
            return Result.error(403, "请先绑定内部员工身份后签到");
        }
        return Result.success(profile);
    }

    private String normalizeType(String type) {
        if ("OFF_DUTY".equals(type)) {
            return "OFF_DUTY";
        }
        return "ON_DUTY";
    }

    private MobileCheckinResponse toResponse(StaffCheckin checkin) {
        MobileCheckinResponse response = new MobileCheckinResponse();
        response.setId(checkin.getId());
        response.setSysUserId(checkin.getSysUserId());
        sysUserRepository.findById(checkin.getSysUserId()).ifPresent(user -> applyUser(response, user));
        response.setCheckinTime(checkin.getCheckinTime());
        response.setCheckinType(checkin.getCheckinType());
        response.setCheckinTypeText("OFF_DUTY".equals(checkin.getCheckinType()) ? "下班签退" : "上班签到");
        response.setLocation(checkin.getLocation());
        response.setLongitude(checkin.getLongitude());
        response.setLatitude(checkin.getLatitude());
        response.setRemark(checkin.getRemark());
        response.setStatus(checkin.getStatus());
        response.setCreatedTime(checkin.getCreatedTime());
        return response;
    }

    private void applyUser(MobileCheckinResponse response, SysUser user) {
        response.setSysUserName(user.getUsername());
        response.setSysRealName(user.getRealName());
    }
}
