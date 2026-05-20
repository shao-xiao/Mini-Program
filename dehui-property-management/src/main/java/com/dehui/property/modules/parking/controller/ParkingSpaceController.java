package com.dehui.property.modules.parking.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.parking.dto.ParkingBindRequest;
import com.dehui.property.modules.parking.dto.ParkingOperationLogResponse;
import com.dehui.property.modules.parking.dto.ParkingReleaseRequest;
import com.dehui.property.modules.parking.dto.ParkingSpaceRequest;
import com.dehui.property.modules.parking.dto.ParkingSpaceResponse;
import com.dehui.property.modules.parking.service.ParkingSpaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/parking/spaces")
@RequiredArgsConstructor
public class ParkingSpaceController {
    private final ParkingSpaceService parkingSpaceService;

    @GetMapping
    public Result<List<ParkingSpaceResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String area) {
        return Result.success(parkingSpaceService.list(keyword, status, area));
    }

    @GetMapping("/{id}")
    public Result<ParkingSpaceResponse> get(@PathVariable Long id) {
        return Result.success(parkingSpaceService.getResponse(id));
    }

    @PostMapping
    public Result<ParkingSpaceResponse> create(@RequestBody ParkingSpaceRequest request) {
        return Result.success(parkingSpaceService.create(request));
    }

    @PutMapping("/{id}")
    public Result<ParkingSpaceResponse> update(@PathVariable Long id, @RequestBody ParkingSpaceRequest request) {
        return Result.success(parkingSpaceService.update(id, request));
    }

    @PostMapping("/{id}/bind")
    public Result<ParkingSpaceResponse> bind(@PathVariable Long id, @RequestBody ParkingBindRequest request) {
        return Result.success(parkingSpaceService.bind(id, request));
    }

    @PatchMapping("/{id}/bind")
    public Result<ParkingSpaceResponse> bindLegacy(
            @PathVariable Long id,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(defaultValue = "false") Boolean vip,
            @RequestParam String plateNumber) {
        ParkingBindRequest request = new ParkingBindRequest();
        request.setPartyType(Boolean.TRUE.equals(vip) ? "vip" : "tenant");
        request.setTenantId(tenantId);
        request.setPartyName(Boolean.TRUE.equals(vip) ? "VIP" : null);
        request.setPlateNo(plateNumber);
        request.setBillingType("monthly");
        request.setMonthlyFee(new java.math.BigDecimal("300.00"));
        return Result.success(parkingSpaceService.bind(id, request));
    }

    @PostMapping("/{id}/release")
    public Result<ParkingSpaceResponse> release(@PathVariable Long id, @RequestBody(required = false) ParkingReleaseRequest request) {
        return Result.success(parkingSpaceService.release(id, request == null ? new ParkingReleaseRequest() : request));
    }

    @PatchMapping("/{id}/release")
    public Result<ParkingSpaceResponse> releaseLegacy(@PathVariable Long id) {
        return Result.success(parkingSpaceService.release(id, new ParkingReleaseRequest()));
    }

    @PatchMapping("/{id}/status")
    public Result<ParkingSpaceResponse> updateStatus(@PathVariable Long id, @RequestParam String status) {
        return Result.success(parkingSpaceService.updateStatus(id, status));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        parkingSpaceService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}/history")
    public Result<List<ParkingOperationLogResponse>> history(@PathVariable Long id) {
        return Result.success(parkingSpaceService.history(id));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(parkingSpaceService.stats());
    }
}
