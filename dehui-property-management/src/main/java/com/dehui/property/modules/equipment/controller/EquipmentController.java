package com.dehui.property.modules.equipment.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.equipment.dto.EquipmentCreateRequest;
import com.dehui.property.modules.equipment.dto.EquipmentResponse;
import com.dehui.property.modules.equipment.service.EquipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/equipments")
@RequiredArgsConstructor
public class EquipmentController {
    private final EquipmentService equipmentService;

    @PostMapping
    public Result<EquipmentResponse> create(@Valid @RequestBody EquipmentCreateRequest request) {
        return equipmentService.create(request);
    }

    @GetMapping
    public Result<List<EquipmentResponse>> list() {
        return equipmentService.findAll();
    }

    @GetMapping("/{id}")
    public Result<EquipmentResponse> detail(@PathVariable Long id) {
        return equipmentService.findById(id);
    }

    @PatchMapping("/{id}/status")
    public Result<EquipmentResponse> updateStatus(@PathVariable Long id, @RequestParam String status) {
        return equipmentService.updateStatus(id, status);
    }
}
