package com.dehui.property.modules.mobile.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.bill.repository.BillRepository;
import com.dehui.property.modules.meeting.repository.MeetingRoomRepository;
import com.dehui.property.modules.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@Profile("dev")
@RestController
@RequestMapping("/mobile/dev")
@RequiredArgsConstructor
public class MobileDevController {

    private final TenantRepository tenantRepository;
    private final MeetingRoomRepository meetingRoomRepository;
    private final BillRepository billRepository;

    @GetMapping("/fixtures")
    public Result<Map<String, Object>> fixtures() {
        Long testTenantId = tenantRepository.findFirstByTenantName("德汇测试租户")
                .map(tenant -> tenant.getId())
                .orElse(null);

        return Result.success(Map.of(
                "profile", "dev",
                "time", LocalDateTime.now().toString(),
                "testTenantId", testTenantId == null ? "" : testTenantId,
                "testTenantName", "德汇测试租户",
                "meetingRoomCount", meetingRoomRepository.count(),
                "billCount", billRepository.count(),
                "adminUsername", "admin"
        ));
    }
}
