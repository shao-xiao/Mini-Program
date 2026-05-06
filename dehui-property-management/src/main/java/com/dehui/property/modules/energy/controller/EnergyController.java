package com.dehui.property.modules.energy.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.energy.entity.EnergyRecord;
import com.dehui.property.modules.energy.service.EnergyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/energy")
@RequiredArgsConstructor
public class EnergyController {
    private final EnergyService energyService;

    @GetMapping("/list")
    public Result<List<EnergyRecord>> list() {
        return Result.success(energyService.findAll());
    }

    @PostMapping("/save")
    public Result<EnergyRecord> save(@RequestBody EnergyRecord energyRecord) {
        return Result.success(energyService.save(energyRecord));
    }
}
