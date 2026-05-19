package com.dehui.property.modules.asset.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.asset.dto.AssetMaintenanceRequest;
import com.dehui.property.modules.asset.dto.AssetOperationLogResponse;
import com.dehui.property.modules.asset.dto.AssetRequest;
import com.dehui.property.modules.asset.dto.AssetResponse;
import com.dehui.property.modules.asset.dto.AssetStatusRequest;
import com.dehui.property.modules.asset.dto.AssetTransferRequest;
import com.dehui.property.modules.asset.service.AssetService;
import jakarta.validation.Valid;
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
@RequestMapping("/assets")
@RequiredArgsConstructor
public class AssetController {
    private final AssetService assetService;

    @GetMapping
    public Result<List<AssetResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String assetType,
            @RequestParam(required = false) String assetCategory,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long floorId,
            @RequestParam(required = false) Long roomId) {
        return assetService.findAll(keyword, assetType, assetCategory, status, buildingId, floorId, roomId);
    }

    @GetMapping("/{id}")
    public Result<AssetResponse> detail(@PathVariable Long id) {
        return assetService.findById(id);
    }

    @PostMapping
    public Result<AssetResponse> create(@Valid @RequestBody AssetRequest request) {
        return assetService.create(request);
    }

    @PutMapping("/{id}")
    public Result<AssetResponse> update(@PathVariable Long id, @Valid @RequestBody AssetRequest request) {
        return assetService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        return assetService.delete(id);
    }

    @PatchMapping("/{id}/status")
    public Result<AssetResponse> updateStatus(@PathVariable Long id, @Valid @RequestBody AssetStatusRequest request) {
        return assetService.updateStatus(id, request);
    }

    @PostMapping("/{id}/transfer")
    public Result<AssetResponse> transfer(@PathVariable Long id, @Valid @RequestBody AssetTransferRequest request) {
        return assetService.transfer(id, request);
    }

    @PostMapping("/{id}/maintenance")
    public Result<AssetResponse> maintenance(@PathVariable Long id, @RequestBody AssetMaintenanceRequest request) {
        return assetService.maintenance(id, request);
    }

    @GetMapping("/{id}/logs")
    public Result<List<AssetOperationLogResponse>> logs(@PathVariable Long id) {
        return assetService.logs(id);
    }

    @GetMapping("/statistics/overview")
    public Result<Map<String, Object>> overview() {
        return Result.success(assetService.overview());
    }

    @GetMapping("/statistics/by-type")
    public Result<Map<String, Long>> byType() {
        return Result.success(assetService.countBy("type"));
    }

    @GetMapping("/statistics/by-status")
    public Result<Map<String, Long>> byStatus() {
        return Result.success(assetService.countBy("status"));
    }

    @GetMapping("/statistics/by-floor")
    public Result<Map<String, Long>> byFloor() {
        return Result.success(assetService.countBy("floor"));
    }

    @GetMapping("/statistics/warranty-expiring")
    public Result<List<AssetResponse>> warrantyExpiring() {
        return Result.success(assetService.warrantyExpiring());
    }

    @GetMapping("/statistics/maintenance-due")
    public Result<List<AssetResponse>> maintenanceDue() {
        return Result.success(assetService.maintenanceDue());
    }
}
