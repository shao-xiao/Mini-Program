package com.dehui.property.modules.parking.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.parking.entity.ParkingSpace;
import com.dehui.property.modules.parking.service.ParkingSpaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/parking/spaces")
@RequiredArgsConstructor
public class ParkingSpaceController {

    private final ParkingSpaceService parkingSpaceService;

    @PostMapping
    public Result<ParkingSpace> create(@RequestBody ParkingSpace parkingSpace) {
        return Result.success(parkingSpaceService.create(parkingSpace));
    }

    @PutMapping("/{id}")
    public Result<ParkingSpace> update(@PathVariable Long id, @RequestBody ParkingSpace parkingSpace) {
        return Result.success(parkingSpaceService.update(id, parkingSpace));
    }

    @GetMapping
    public Result<List<ParkingSpace>> list() {
        return Result.success(parkingSpaceService.list());
    }

    @GetMapping("/{id}")
    public Result<ParkingSpace> get(@PathVariable Long id) {
        return Result.success(parkingSpaceService.get(id));
    }

    @PatchMapping("/{id}/bind")
    public Result<ParkingSpace> bind(
            @PathVariable Long id,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(defaultValue = "false") Boolean vip,
            @RequestParam String plateNumber) {
        return Result.success(parkingSpaceService.bindToTenant(id, tenantId, vip, plateNumber));
    }

    @PatchMapping("/{id}/release")
    public Result<ParkingSpace> release(@PathVariable Long id) {
        return Result.success(parkingSpaceService.release(id));
    }

    @PatchMapping("/{id}/status")
    public Result<ParkingSpace> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        return Result.success(parkingSpaceService.updateStatus(id, status));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        parkingSpaceService.delete(id);
        return Result.success(null);
    }

    @GetMapping("/stats")
    public Result<java.util.Map<String, Object>> stats() {
        return Result.success(parkingSpaceService.stats());
    }
}
