package com.dehui.property.modules.building.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.building.dto.FloorCreateRequest;
import com.dehui.property.modules.building.dto.FloorResponse;
import com.dehui.property.modules.building.dto.FloorStatsResponse;
import com.dehui.property.modules.building.dto.FloorUpdateRequest;
import com.dehui.property.modules.building.service.BuildingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/buildings/{buildingId}/floors")
@RequiredArgsConstructor
public class FloorController {
    private final BuildingService buildingService;

    @GetMapping
    public Result<List<FloorResponse>> list(@PathVariable Long buildingId) {
        return buildingService.listFloors(buildingId);
    }

    @GetMapping("/{floorId}")
    public Result<FloorResponse> detail(@PathVariable Long buildingId, @PathVariable Long floorId) {
        return buildingService.findFloorById(buildingId, floorId);
    }

    @PostMapping
    public Result<FloorResponse> create(@PathVariable Long buildingId, @Valid @RequestBody FloorCreateRequest request) {
        return buildingService.createFloor(buildingId, request);
    }

    @PutMapping("/{floorId}")
    public Result<FloorResponse> update(@PathVariable Long buildingId,
                                        @PathVariable Long floorId,
                                        @Valid @RequestBody FloorUpdateRequest request) {
        return buildingService.updateFloor(buildingId, floorId, request);
    }

    @DeleteMapping("/{floorId}")
    public Result<Void> delete(@PathVariable Long buildingId, @PathVariable Long floorId) {
        return buildingService.deleteFloor(buildingId, floorId);
    }

    @GetMapping("/{floorId}/stats")
    public Result<FloorStatsResponse> stats(@PathVariable Long buildingId, @PathVariable Long floorId) {
        return buildingService.getFloorStats(buildingId, floorId);
    }
}
