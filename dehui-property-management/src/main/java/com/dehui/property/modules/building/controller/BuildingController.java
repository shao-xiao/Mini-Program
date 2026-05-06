package com.dehui.property.modules.building.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.building.dto.BuildingCreateRequest;
import com.dehui.property.modules.building.dto.BuildingResponse;
import com.dehui.property.modules.building.dto.BuildingStatsResponse;
import com.dehui.property.modules.building.dto.BuildingUpdateRequest;
import com.dehui.property.modules.building.service.BuildingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/buildings")
@RequiredArgsConstructor
public class BuildingController {
    private final BuildingService buildingService;

    @GetMapping
    public Result<Page<BuildingResponse>> list(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        return Result.success(buildingService.findAll(page, size));
    }

    @GetMapping("/{id}")
    public Result<BuildingResponse> detail(@PathVariable Long id) {
        return buildingService.findById(id);
    }

    @PostMapping
    public Result<BuildingResponse> create(@Valid @RequestBody BuildingCreateRequest request) {
        return Result.success(buildingService.save(request));
    }

    @PutMapping("/{id}")
    public Result<BuildingResponse> update(@PathVariable Long id, @Valid @RequestBody BuildingUpdateRequest request) {
        return buildingService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        return buildingService.delete(id);
    }

    @GetMapping("/{id}/stats")
    public Result<BuildingStatsResponse> stats(@PathVariable Long id) {
        return buildingService.getBuildingStats(id);
    }
}
