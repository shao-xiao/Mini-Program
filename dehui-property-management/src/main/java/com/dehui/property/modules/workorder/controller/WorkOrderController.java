package com.dehui.property.modules.workorder.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.system.entity.SysUser;
import com.dehui.property.modules.system.service.SystemUserService;
import com.dehui.property.modules.workorder.dto.AssignableUserResponse;
import com.dehui.property.modules.workorder.dto.WorkOrderAssignRequest;
import com.dehui.property.modules.workorder.dto.WorkOrderCompleteRequest;
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
    private final SystemUserService systemUserService;

    @PostMapping
    public Result<WorkOrderResponse> create(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody WorkOrderCreateRequest request) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        SysUser currentUser = systemUserService.getByToken(token);
        if (currentUser == null) {
            return Result.error(401, "未登录或登录已过期");
        }

        request.setReporterId(currentUser.getId());
        return workOrderService.create(request);
    }

    @GetMapping
    public Result<List<WorkOrderResponse>> list() {
        return workOrderService.findAll();
    }

    @GetMapping("/assignable-users")
    public Result<List<AssignableUserResponse>> assignableUsers() {
        return Result.success(workOrderService.listAssignableUsers());
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
    public Result<WorkOrderResponse> complete(@PathVariable Long id,
                                              @RequestBody(required = false) WorkOrderCompleteRequest request) {
        return workOrderService.complete(id, request);
    }

    @PostMapping("/{id}/generate-bill")
    public Result<WorkOrderResponse> generateBill(@PathVariable Long id) {
        return workOrderService.generateBill(id);
    }

    @PatchMapping("/{id}/close")
    public Result<WorkOrderResponse> close(@PathVariable Long id) {
        return workOrderService.close(id);
    }
}
