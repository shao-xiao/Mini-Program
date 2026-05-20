package com.dehui.property.modules.mobile.service;

import com.dehui.property.modules.announcement.repository.AnnouncementRepository;
import com.dehui.property.modules.bill.repository.BillRepository;
import com.dehui.property.modules.building.repository.RoomRepository;
import com.dehui.property.modules.meeting.repository.MeetingBookingRepository;
import com.dehui.property.modules.mobile.dto.MobileMeResponse;
import com.dehui.property.modules.mobile.dto.MobileMineSummaryResponse;
import com.dehui.property.modules.mobile.entity.WechatUser;
import com.dehui.property.modules.system.repository.SysUserRepository;
import com.dehui.property.modules.tenant.repository.TenantRepository;
import com.dehui.property.modules.visitor.repository.VisitorRecordRepository;
import com.dehui.property.modules.workorder.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MobileMineService {

    private final MobileAuthService mobileAuthService;
    private final AnnouncementRepository announcementRepository;
    private final RoomRepository roomRepository;
    private final BillRepository billRepository;
    private final MeetingBookingRepository meetingBookingRepository;
    private final WorkOrderRepository workOrderRepository;
    private final VisitorRecordRepository visitorRecordRepository;
    private final TenantRepository tenantRepository;
    private final SysUserRepository sysUserRepository;

    public MobileMeResponse me(String token) {
        WechatUser user = mobileAuthService.getByToken(normalizeToken(token));
        if (user == null) {
            return new MobileMeResponse(false, "guest", false, false, null, null);
        }

        boolean tenantBound = user.getBoundTenantId() != null;
        boolean staffBound = user.getBoundSysUserId() != null;
        String role = staffBound ? "staff" : (tenantBound ? "tenant" : "guest");

        MobileMeResponse.BoundInfo tenantInfo = tenantBound
                ? tenantRepository.findById(user.getBoundTenantId())
                        .map(tenant -> new MobileMeResponse.BoundInfo(tenant.getId(), tenant.getTenantName(), null))
                        .orElse(null)
                : null;
        MobileMeResponse.BoundInfo staffInfo = staffBound
                ? sysUserRepository.findById(user.getBoundSysUserId())
                        .map(staff -> new MobileMeResponse.BoundInfo(staff.getId(), staff.getRealName(), staff.getUsername()))
                        .orElse(null)
                : null;

        return new MobileMeResponse(true, role, tenantBound, staffBound, tenantInfo, staffInfo);
    }

    public MobileMineSummaryResponse summary(String token) {
        WechatUser user = mobileAuthService.getByToken(normalizeToken(token));
        Long tenantId = user == null ? null : user.getBoundTenantId();
        Long mobileUserId = user == null ? null : user.getId();
        boolean needTenantBind = tenantId == null;

        Long billCount = needTenantBind ? null : billRepository.countByTenantId(tenantId);
        Long meetingCount = needTenantBind ? null : meetingBookingRepository.countByTenantId(tenantId);
        Long workOrderCount = needTenantBind ? null : workOrderRepository.countByMobileUserId(mobileUserId);
        Long visitorCount = needTenantBind ? null : visitorRecordRepository.countByMobileUserId(mobileUserId);

        return new MobileMineSummaryResponse(
                announcementRepository.findByStatusOrderByPublishTimeDesc("PUBLISHED").size(),
                safeCount(roomRepository.countByStatus("AVAILABLE")),
                billCount,
                meetingCount,
                workOrderCount,
                visitorCount,
                needTenantBind
        );
    }

    private String normalizeToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring(7);
        }
        return token;
    }

    private long safeCount(Long value) {
        return value == null ? 0 : value;
    }
}
