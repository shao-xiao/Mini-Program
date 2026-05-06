package com.dehui.property.modules.inspection.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.inspection.dto.InspectionCreateRequest;
import com.dehui.property.modules.inspection.entity.InspectionRecord;
import com.dehui.property.modules.inspection.service.InspectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
    public Result<List<InspectionRecord>> list() {
        return Result.success(service.list());
    }

    @PostMapping("/{id}/close")
    public Result<InspectionRecord> close(@PathVariable Long id) {
        return Result.success(service.close(id));
    }
}
