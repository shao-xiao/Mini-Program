package com.dehui.property.modules.mobile.service;

import com.dehui.property.common.Result;
import com.dehui.property.modules.mobile.dto.MobileUserProfile;
import com.dehui.property.modules.mobile.dto.MobileVisitorHomeResponse;
import com.dehui.property.modules.mobile.dto.MobileVisitorRequest;
import com.dehui.property.modules.mobile.dto.MobileVisitorResponse;
import com.dehui.property.modules.tenant.repository.TenantRepository;
import com.dehui.property.modules.visitor.entity.VisitorRecord;
import com.dehui.property.modules.visitor.repository.VisitorRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MobileVisitorService {

    private final MobileAuthService mobileAuthService;
    private final VisitorRecordRepository visitorRecordRepository;
    private final TenantRepository tenantRepository;

    public Result<MobileVisitorHomeResponse> home(String token) {
        MobileUserProfile profile = mobileAuthService.getProfile(token);
        if (profile == null) {
            return Result.error(401, "未登录或登录已过期");
        }

        List<MobileVisitorResponse> visitors = visitorRecordRepository.findByMobileUserIdOrderByVisitTimeDesc(profile.getId())
                .stream()
                .map(this::toResponse)
                .toList();
        return Result.success(new MobileVisitorHomeResponse(profile, visitors));
    }

    @Transactional
    public Result<MobileVisitorResponse> create(String token, MobileVisitorRequest request) {
        MobileUserProfile profile = mobileAuthService.getProfile(token);
        if (profile == null) {
            return Result.error(401, "未登录或登录已过期");
        }

        Long tenantId = request.getTenantId() != null ? request.getTenantId() : profile.getBoundTenantId();

        VisitorRecord record = new VisitorRecord();
        record.setVisitorName(request.getVisitorName());
        record.setVisitorPhone(request.getVisitorPhone());
        record.setTenantId(tenantId);
        record.setMobileUserId(profile.getId());
        record.setVisitedPerson(request.getVisitedPerson());
        record.setVisitReason(request.getVisitReason());
        record.setVisitTime(request.getVisitTime());
        record.setStatus("REGISTERED");
        record.setSource("MINIPROGRAM");
        record.setRemark(buildRemark(request));

        return Result.success(toResponse(visitorRecordRepository.save(record)));
    }

    @Transactional
    public Result<MobileVisitorResponse> cancel(String token, Long id) {
        MobileUserProfile profile = mobileAuthService.getProfile(token);
        if (profile == null) {
            return Result.error(401, "未登录或登录已过期");
        }

        VisitorRecord record = visitorRecordRepository.findById(id).orElse(null);
        if (record == null) {
            return Result.error("访客预约不存在");
        }
        if (record.getMobileUserId() == null || !record.getMobileUserId().equals(profile.getId())) {
            return Result.error(403, "不能取消他人的访客预约");
        }
        if (!"REGISTERED".equals(record.getStatus())) {
            return Result.error("当前状态不能取消");
        }

        record.setStatus("CANCELLED");
        return Result.success(toResponse(visitorRecordRepository.save(record)));
    }

    private String buildRemark(MobileVisitorRequest request) {
        StringBuilder remark = new StringBuilder();
        if (request.getCarPlateNo() != null && !request.getCarPlateNo().isBlank()) {
            remark.append("车牌：").append(request.getCarPlateNo()).append("；");
        }
        if (request.getRemark() != null && !request.getRemark().isBlank()) {
            remark.append(request.getRemark());
        }
        return remark.toString();
    }

    private MobileVisitorResponse toResponse(VisitorRecord record) {
        MobileVisitorResponse response = new MobileVisitorResponse();
        response.setId(record.getId());
        response.setVisitorName(record.getVisitorName());
        response.setVisitorPhone(record.getVisitorPhone());
        response.setTenantId(record.getTenantId());
        if (record.getTenantId() != null) {
            tenantRepository.findById(record.getTenantId())
                    .ifPresent(tenant -> response.setTenantName(tenant.getTenantName()));
        }
        response.setVisitedPerson(record.getVisitedPerson());
        response.setVisitReason(record.getVisitReason());
        response.setVisitTime(record.getVisitTime());
        response.setLeaveTime(record.getLeaveTime());
        response.setStatus(record.getStatus());
        response.setStatusText(toStatusText(record.getStatus()));
        response.setRemark(record.getRemark());
        response.setCreatedTime(record.getCreatedTime());
        return response;
    }

    private String toStatusText(String status) {
        if ("REGISTERED".equals(status)) return "已预约";
        if ("ENTERED".equals(status)) return "已到访";
        if ("LEFT".equals(status)) return "已离场";
        if ("CANCELLED".equals(status)) return "已取消";
        return status == null || status.isBlank() ? "未知" : status;
    }
}
