package com.dehui.property.modules.workorder.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.workorder.dto.WorkOrderAssignRequest;
import com.dehui.property.modules.workorder.dto.WorkOrderCreateRequest;
import com.dehui.property.modules.workorder.dto.WorkOrderResponse;
import com.dehui.property.modules.workorder.service.WorkOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/workorders")
@RequiredArgsConstructor
public class WorkOrderController {
    private final WorkOrderService workOrderService;

    @PostMapping
    public Result<WorkOrderResponse> create(@Valid @RequestBody WorkOrderCreateRequest request) {
        return workOrderService.create(request);
    }

    @GetMapping
    public Result<List<WorkOrderResponse>> list() {
        return workOrderService.findAll();
    }

    @GetMapping("/{id}")
    public Result<WorkOrderResponse> detail(@PathVariable Long id) {
        return workOrderService.findById(id);
    }

    @PatchMapping("/{id}/assign")
    public Result<WorkOrderResponse> assign(@PathVariable Long id,
                                            @Valid @RequestBody WorkOrderAssignRequest request) {
        return workOrderService.assign(id, request);
    }

    @PatchMapping("/{id}/start")
    public Result<WorkOrderResponse> start(@PathVariable Long id) {
        return workOrderService.start(id);
    }

    @PatchMapping("/{id}/complete")
    public Result<WorkOrderResponse> complete(@PathVariable Long id) {
        return workOrderService.complete(id);
    }

    @PatchMapping("/{id}/close")
    public Result<WorkOrderResponse> close(@PathVariable Long id) {
        return workOrderService.close(id);
    }
}
