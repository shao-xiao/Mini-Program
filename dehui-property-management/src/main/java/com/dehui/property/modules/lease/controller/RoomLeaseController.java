package com.dehui.property.modules.lease.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.lease.dto.RoomLeaseCreateRequest;
import com.dehui.property.modules.lease.dto.RoomLeaseResponse;
import com.dehui.property.modules.lease.service.RoomLeaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomLeaseController {
    private final RoomLeaseService roomLeaseService;

    @PostMapping("/{roomId}/lease")
    public Result<RoomLeaseResponse> checkin(@PathVariable Long roomId,
                                              @Valid @RequestBody RoomLeaseCreateRequest request) {
        return roomLeaseService.checkin(roomId, request);
    }

    @PostMapping("/{roomId}/checkout")
    public Result<Void> checkout(@PathVariable Long roomId) {
        return roomLeaseService.checkout(roomId);
    }

    @GetMapping("/{roomId}/tenant")
    public Result<RoomLeaseResponse> getCurrentTenant(@PathVariable Long roomId) {
        return roomLeaseService.getCurrentLease(roomId);
    }

    @GetMapping("/tenants/{tenantId}/rooms")
    public Result<List<RoomLeaseResponse>> getTenantRooms(@PathVariable Long tenantId) {
        return roomLeaseService.getTenantLeases(tenantId);
    }
}