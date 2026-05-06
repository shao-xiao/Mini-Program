package com.dehui.property.modules.parking.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.parking.entity.ParkingBill;
import com.dehui.property.modules.parking.service.ParkingBillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/parking/bills")
@RequiredArgsConstructor
public class ParkingBillController {

    private final ParkingBillService parkingBillService;

    @PostMapping
    public Result<ParkingBill> create(@RequestBody ParkingBill bill) {
        return Result.success(parkingBillService.create(bill));
    }

    @GetMapping
    public Result<List<ParkingBill>> list() {
        return Result.success(parkingBillService.list());
    }

    @GetMapping("/tenants/{tenantId}")
    public Result<List<ParkingBill>> listByTenant(@PathVariable Long tenantId) {
        return Result.success(parkingBillService.listByTenant(tenantId));
    }

    @GetMapping("/spaces/{parkingSpaceId}")
    public Result<List<ParkingBill>> listBySpace(@PathVariable Long parkingSpaceId) {
        return Result.success(parkingBillService.listBySpace(parkingSpaceId));
    }

    @PostMapping("/{id}/pay")
    public Result<ParkingBill> pay(@PathVariable Long id) {
        return Result.success(parkingBillService.pay(id));
    }

    @GetMapping("/stats")
    public Result<java.util.Map<String, Object>> stats() {
        return Result.success(parkingBillService.stats());
    }
}