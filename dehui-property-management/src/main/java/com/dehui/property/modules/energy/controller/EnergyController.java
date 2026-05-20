package com.dehui.property.modules.energy.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.energy.dto.EnergyAnomalyStatusRequest;
import com.dehui.property.modules.energy.dto.EnergyLastReadingResponse;
import com.dehui.property.modules.energy.dto.EnergyMeterResponse;
import com.dehui.property.modules.energy.dto.EnergyReadingRequest;
import com.dehui.property.modules.energy.dto.EnergyReadingResponse;
import com.dehui.property.modules.energy.dto.EnergyStatsResponse;
import com.dehui.property.modules.energy.entity.EnergyRecord;
import com.dehui.property.modules.energy.entity.EnergyRateRule;
import com.dehui.property.modules.energy.service.EnergyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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

    @GetMapping("/meters")
    public Result<List<EnergyMeterResponse>> meters(@RequestParam(required = false) String meterType,
                                                    @RequestParam(required = false) Long buildingId,
                                                    @RequestParam(required = false) Long floorId,
                                                    @RequestParam(required = false) Long roomId,
                                                    @RequestParam(required = false) Long tenantId) {
        return energyService.listMeters(meterType, buildingId, floorId, roomId, tenantId);
    }

    @GetMapping("/meters/{id}")
    public Result<EnergyMeterResponse> meter(@PathVariable Long id) {
        return energyService.findMeter(id);
    }

    @GetMapping("/meters/{id}/last-reading")
    public Result<EnergyLastReadingResponse> lastReading(@PathVariable Long id) {
        return energyService.lastReading(id);
    }

    @GetMapping("/readings")
    public Result<Page<EnergyReadingResponse>> readings(@RequestParam(required = false) String meterType,
                                                        @RequestParam(required = false) String meterNo,
                                                        @RequestParam(required = false) String periodMonth,
                                                        @RequestParam(required = false) Long buildingId,
                                                        @RequestParam(required = false) Long floorId,
                                                        @RequestParam(required = false) Long roomId,
                                                        @RequestParam(required = false) Long tenantId,
                                                        @RequestParam(required = false) String billStatus,
                                                        @RequestParam(required = false) Boolean abnormalFlag,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "10") int size) {
        return energyService.listReadings(meterType, meterNo, periodMonth, buildingId, floorId, roomId, tenantId,
                billStatus, abnormalFlag, page, size);
    }

    @GetMapping("/readings/{id}")
    public Result<EnergyReadingResponse> reading(@PathVariable Long id) {
        return energyService.findReading(id);
    }

    @PostMapping("/readings")
    public Result<EnergyReadingResponse> createReading(@Valid @RequestBody EnergyReadingRequest request) {
        return energyService.createReading(request);
    }

    @PutMapping("/readings/{id}")
    public Result<EnergyReadingResponse> updateReading(@PathVariable Long id,
                                                       @Valid @RequestBody EnergyReadingRequest request) {
        return energyService.updateReading(id, request);
    }

    @DeleteMapping("/readings/{id}")
    public Result<Void> deleteReading(@PathVariable Long id) {
        return energyService.deleteReading(id);
    }

    @PostMapping("/readings/{id}/generate-bill")
    public Result<EnergyReadingResponse> generateReadingBill(@PathVariable Long id) {
        return energyService.generateReadingBill(id);
    }

    @PostMapping("/readings/{id}/mark-posted")
    public Result<EnergyReadingResponse> markPosted(@PathVariable Long id) {
        return energyService.markPosted(id);
    }

    @PatchMapping("/readings/{id}/anomaly-status")
    public Result<EnergyReadingResponse> updateAnomalyStatus(@PathVariable Long id,
                                                             @RequestBody EnergyAnomalyStatusRequest request) {
        return energyService.updateAnomalyStatus(id, request);
    }

    @GetMapping("/stats")
    public Result<EnergyStatsResponse> stats(@RequestParam(required = false) String meterType,
                                             @RequestParam(required = false) String periodMonth,
                                             @RequestParam(required = false) Long buildingId,
                                             @RequestParam(required = false) Long floorId,
                                             @RequestParam(required = false) Long roomId,
                                             @RequestParam(required = false) Long tenantId,
                                             @RequestParam(required = false) Boolean abnormalFlag,
                                             @RequestParam(required = false) String billStatus) {
        return energyService.stats(meterType, periodMonth, buildingId, floorId, roomId, tenantId, abnormalFlag, billStatus);
    }
}
