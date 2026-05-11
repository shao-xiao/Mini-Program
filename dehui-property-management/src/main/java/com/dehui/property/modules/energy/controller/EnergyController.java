package com.dehui.property.modules.energy.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.energy.entity.EnergyRecord;
import com.dehui.property.modules.energy.entity.EnergyRateRule;
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

    @GetMapping("/rate-rules")
    public Result<List<EnergyRateRule>> rules() {
        return Result.success(energyService.findRules());
    }

    @PostMapping("/rate-rules")
    public Result<EnergyRateRule> saveRule(@RequestBody EnergyRateRule rule) {
        return Result.success(energyService.saveRule(rule));
    }

    @PutMapping("/rate-rules/{id}")
    public Result<EnergyRateRule> updateRule(@PathVariable Long id, @RequestBody EnergyRateRule rule) {
        return energyService.updateRule(id, rule);
    }

    @DeleteMapping("/rate-rules/{id}")
    public Result<Void> deleteRule(@PathVariable Long id) {
        return energyService.deleteRule(id);
    }

    @PostMapping("/records/{id}/generate-bill")
    public Result<EnergyRecord> generateBill(@PathVariable Long id) {
        return energyService.generateBill(id);
    }
}
