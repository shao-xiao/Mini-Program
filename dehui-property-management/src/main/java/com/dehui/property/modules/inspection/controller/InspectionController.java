package com.dehui.property.modules.inspection.controller;

import com.dehui.property.common.ExcelExportUtil;
import com.dehui.property.common.Result;
import com.dehui.property.modules.inspection.dto.InspectionCreateRequest;
import com.dehui.property.modules.inspection.dto.InspectionPlanRequest;
import com.dehui.property.modules.inspection.entity.InspectionPlan;
import com.dehui.property.modules.inspection.entity.InspectionRecord;
import com.dehui.property.modules.inspection.service.InspectionService;
import com.dehui.property.modules.workorder.dto.WorkOrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/inspections")
@RequiredArgsConstructor
public class InspectionController {

    private final InspectionService service;

    @PostMapping
    public Result<InspectionRecord> create(@RequestBody InspectionCreateRequest req) {
        return Result.success(service.create(req));
    }

    @GetMapping
    public Result<List<InspectionRecord>> list(
            @RequestParam(required = false) String inspectionType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String inspector,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(service.list(inspectionType, status, result, inspector, area, startDate, endDate));
    }

    @GetMapping("/{id}")
    public Result<InspectionRecord> get(@PathVariable Long id) {
        return Result.success(service.get(id));
    }

    @PutMapping("/{id}")
    public Result<InspectionRecord> update(@PathVariable Long id, @RequestBody InspectionCreateRequest req) {
        return Result.success(service.update(id, req));
    }

    @PostMapping("/{id}/close")
    public Result<InspectionRecord> close(@PathVariable Long id) {
        return Result.success(service.close(id));
    }

    @PostMapping("/{id}/convert-workorder")
    public Result<WorkOrderResponse> convertToWorkOrder(@PathVariable Long id) {
        return Result.success(service.convertToWorkOrder(id));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String inspectionType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String inspector,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ExcelExportUtil.response("巡检管理.xlsx", service.export(inspectionType, status, result, inspector, area, startDate, endDate));
    }

    @GetMapping("/plans")
    public Result<List<InspectionPlan>> listPlans() {
        return Result.success(service.listPlans());
    }

    @PostMapping("/plans")
    public Result<InspectionPlan> createPlan(@RequestBody InspectionPlanRequest request) {
        return Result.success(service.createPlan(request));
    }

    @PutMapping("/plans/{id}")
    public Result<InspectionPlan> updatePlan(@PathVariable Long id, @RequestBody InspectionPlanRequest request) {
        return Result.success(service.updatePlan(id, request));
    }

    @DeleteMapping("/plans/{id}")
    public Result<Void> deletePlan(@PathVariable Long id) {
        service.deletePlan(id);
        return Result.success();
    }
}
