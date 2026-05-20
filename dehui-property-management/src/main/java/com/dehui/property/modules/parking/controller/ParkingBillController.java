package com.dehui.property.modules.parking.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.parking.dto.ParkingBillResponse;
import com.dehui.property.modules.parking.dto.ParkingBillSyncRequest;
import com.dehui.property.modules.parking.dto.ParkingBillSyncResponse;
import com.dehui.property.modules.parking.service.ParkingBillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/parking/bills")
@RequiredArgsConstructor
public class ParkingBillController {
    private final ParkingBillService parkingBillService;

    @GetMapping
    public Result<List<ParkingBillResponse>> list(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String syncStatus,
            @RequestParam(required = false) String keyword) {
        if (month == null && status == null && syncStatus == null && keyword == null) {
            return Result.success(parkingBillService.listAll());
        }
        return Result.success(parkingBillService.list(month, status, syncStatus, keyword));
    }

    @GetMapping("/tenants/{tenantId}")
    public Result<List<ParkingBillResponse>> listByTenant(@PathVariable Long tenantId) {
        return Result.success(parkingBillService.listByTenant(tenantId));
    }

    @GetMapping("/spaces/{parkingSpaceId}")
    public Result<List<ParkingBillResponse>> listBySpace(@PathVariable Long parkingSpaceId) {
        return Result.success(parkingBillService.listBySpace(parkingSpaceId));
    }

    @PostMapping("/sync")
    public Result<ParkingBillSyncResponse> sync(@RequestBody(required = false) ParkingBillSyncRequest request) {
        return Result.success(parkingBillService.sync(request == null ? new ParkingBillSyncRequest() : request));
    }

    @PostMapping("/sync-to-bills")
    public Result<ParkingBillSyncResponse> syncLegacy() {
        return Result.success(parkingBillService.sync(new ParkingBillSyncRequest()));
    }

    @PostMapping("/{id}/pay")
    public Result<ParkingBillResponse> pay(@PathVariable Long id) {
        return Result.success(parkingBillService.pay(id));
    }

    @PostMapping("/{id}/void")
    public Result<ParkingBillResponse> voidBill(@PathVariable Long id) {
        return Result.success(parkingBillService.voidBill(id));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(parkingBillService.stats());
    }
}
