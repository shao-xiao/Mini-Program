package com.dehui.property.modules.building.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.building.dto.FloorBatchGenerateRequest;
import com.dehui.property.modules.building.dto.FloorCreateRequest;
import com.dehui.property.modules.building.dto.FloorResponse;
import com.dehui.property.modules.building.dto.FloorUpdateRequest;
import com.dehui.property.modules.building.service.BuildingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/floors")
@RequiredArgsConstructor
public class FlatFloorController {
    private final BuildingService buildingService;

    @GetMapping
    public Result<List<FloorResponse>> list(@RequestParam(name = "building_id") Long buildingId) {
        return buildingService.listFloors(buildingId);
    }

    @PostMapping
    public Result<FloorResponse> create(@Valid @RequestBody FloorCreateRequest request) {
        return buildingService.createFloor(request);
    }

    @PutMapping("/{id}")
    public Result<FloorResponse> update(@PathVariable Long id, @Valid @RequestBody FloorUpdateRequest request) {
        return buildingService.updateFloor(id, request);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        return buildingService.deleteFloor(id);
    }

    @PostMapping("/batch-generate")
    public Result<List<FloorResponse>> batchGenerate(@Valid @RequestBody FloorBatchGenerateRequest request) {
        return buildingService.batchGenerateFloors(request);
    }
}
