package com.dehui.property.modules.workorder.controller;

import com.dehui.property.common.ExcelExportUtil;
import com.dehui.property.common.Result;
import com.dehui.property.modules.system.entity.SysUser;
import com.dehui.property.modules.system.service.SystemUserService;
import com.dehui.property.modules.workorder.dto.AssignableUserResponse;
import com.dehui.property.modules.workorder.dto.WorkOrderAssignRequest;
import com.dehui.property.modules.workorder.dto.WorkOrderCompleteRequest;
import com.dehui.property.modules.workorder.dto.WorkOrderCreateRequest;
import com.dehui.property.modules.workorder.dto.WorkOrderResponse;
import com.dehui.property.modules.workorder.dto.WorkOrderStatusRequest;
import com.dehui.property.modules.workorder.service.WorkOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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
    public Result<List<WorkOrderResponse>> list(
            @RequestParam(required = false) String orderNumber,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String reporter,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Long handlerId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime submittedStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime submittedEnd) {
        return workOrderService.findAll(orderNumber, title, location, category, priority, status, reporter,
                tenantId, handlerId, source, submittedStart, submittedEnd);
    }

    @GetMapping("/assignable-users")
    public Result<List<AssignableUserResponse>> assignableUsers() {
        return Result.success(workOrderService.listAssignableUsers());
    }

    @GetMapping("/{id}")
    public Result<WorkOrderResponse> detail(@PathVariable Long id) {
        return workOrderService.findById(id);
    }

    @PutMapping("/{id}")
    public Result<WorkOrderResponse> update(@PathVariable Long id, @Valid @RequestBody WorkOrderCreateRequest request) {
        return workOrderService.update(id, request);
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

    @PatchMapping("/{id}/status")
    public Result<WorkOrderResponse> changeStatus(@PathVariable Long id, @RequestBody WorkOrderStatusRequest request) {
        return workOrderService.changeStatus(id, request);
    }

    @PatchMapping("/{id}/complete")
    public Result<WorkOrderResponse> complete(@PathVariable Long id,
                                              @RequestBody(required = false) WorkOrderCompleteRequest request) {
        return workOrderService.complete(id, request);
    }

    @PatchMapping("/{id}/confirm")
    public Result<WorkOrderResponse> confirm(@PathVariable Long id) {
        return workOrderService.confirm(id, "admin");
    }

    @PostMapping("/{id}/generate-bill")
    public Result<WorkOrderResponse> generateBill(@PathVariable Long id) {
        return workOrderService.generateBill(id);
    }

    @PatchMapping("/{id}/close")
    public Result<WorkOrderResponse> close(@PathVariable Long id) {
        return workOrderService.close(id);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String orderNumber,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String reporter,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Long handlerId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime submittedStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime submittedEnd) {
        byte[] bytes = workOrderService.export(orderNumber, title, location, category, priority, status, reporter,
                tenantId, handlerId, source, submittedStart, submittedEnd);
        return ExcelExportUtil.response("工单管理.xlsx", bytes);
    }
}
