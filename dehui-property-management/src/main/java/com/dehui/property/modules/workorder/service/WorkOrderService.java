package com.dehui.property.modules.workorder.service;

import com.dehui.property.common.Result;
import com.dehui.property.modules.workorder.dto.WorkOrderAssignRequest;
import com.dehui.property.modules.workorder.dto.WorkOrderCreateRequest;
import com.dehui.property.modules.workorder.dto.WorkOrderResponse;
import com.dehui.property.modules.workorder.entity.WorkOrder;
import com.dehui.property.modules.workorder.repository.WorkOrderRepository;
import com.dehui.property.modules.equipment.repository.EquipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderService {

    private final WorkOrderRepository workOrderRepository;
    private final EquipmentRepository equipmentRepository;

    public Result<List<WorkOrderResponse>> findAll() {
        List<WorkOrderResponse> responses = workOrderRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return Result.success(responses);
    }

    public Result<WorkOrderResponse> findById(Long id) {
        return workOrderRepository.findById(id)
                .map(wo -> Result.success(toResponse(wo)))
                .orElseGet(() -> Result.error("工单不存在"));
    }

    @Transactional
    public Result<WorkOrderResponse> create(WorkOrderCreateRequest request) {

        String orderNumber = generateOrderNumber();

        WorkOrder workOrder = new WorkOrder();
        workOrder.setOrderNumber(orderNumber);
        workOrder.setTitle(request.getTitle());
        workOrder.setDescription(request.getDescription());
        workOrder.setEquipmentId(request.getEquipmentId());
        workOrder.setLocation(request.getLocation());

        // ✅ 新增：工单类型（核心）
        if (request.getOrderType() == null || request.getOrderType().isBlank()) {
            workOrder.setOrderType("REPAIR");
        } else {
            workOrder.setOrderType(request.getOrderType());
        }

        workOrder.setCategory(request.getCategory());
        workOrder.setPriority(request.getPriority());
        workOrder.setReporterId(request.getReporterId());
        workOrder.setStatus("CREATED");

        WorkOrder saved = workOrderRepository.save(workOrder);

        log.info("工单已创建: orderNumber={}, title={}, type={}",
                saved.getOrderNumber(),
                saved.getTitle(),
                saved.getOrderType());

        return Result.success(toResponse(saved));
    }

    @Transactional
    public Result<WorkOrderResponse> assign(Long id, WorkOrderAssignRequest request) {
        return workOrderRepository.findById(id)
                .map(wo -> {
                    if (!"CREATED".equals(wo.getStatus()) && !"ASSIGNED".equals(wo.getStatus())) {
                        return Result.<WorkOrderResponse>error("当前状态不允许指派");
                    }
                    wo.setHandlerId(request.getHandlerId());
                    wo.setStatus("ASSIGNED");

                    WorkOrder saved = workOrderRepository.save(wo);

                    log.info("工单已指派: orderNumber={}, handlerId={}",
                            saved.getOrderNumber(), request.getHandlerId());

                    return Result.success(toResponse(saved));
                })
                .orElseGet(() -> Result.error("工单不存在"));
    }

    @Transactional
    public Result<WorkOrderResponse> start(Long id) {
        return workOrderRepository.findById(id)
                .map(wo -> {
                    if (!"ASSIGNED".equals(wo.getStatus())) {
                        return Result.<WorkOrderResponse>error("当前状态不允许开始处理");
                    }
                    wo.setStatus("PROCESSING");

                    WorkOrder saved = workOrderRepository.save(wo);

                    log.info("工单已开始处理: orderNumber={}", saved.getOrderNumber());

                    return Result.success(toResponse(saved));
                })
                .orElseGet(() -> Result.error("工单不存在"));
    }

    @Transactional
    public Result<WorkOrderResponse> complete(Long id) {
        return workOrderRepository.findById(id)
                .map(wo -> {
                    if (!"PROCESSING".equals(wo.getStatus())) {
                        return Result.<WorkOrderResponse>error("当前状态不允许完成");
                    }
                    wo.setStatus("COMPLETED");

                    WorkOrder saved = workOrderRepository.save(wo);

                    // 设备恢复逻辑（保留你原来的）
                    if (wo.getEquipmentId() != null) {
                        equipmentRepository.findById(wo.getEquipmentId())
                                .ifPresent(equipment -> {
                                    equipment.setStatus("NORMAL");
                                    equipmentRepository.save(equipment);
                                    log.info("工单完成，设备已恢复NORMAL: equipmentId={}", wo.getEquipmentId());
                                });
                    }

                    log.info("工单已完成: orderNumber={}", saved.getOrderNumber());

                    return Result.success(toResponse(saved));
                })
                .orElseGet(() -> Result.error("工单不存在"));
    }

    @Transactional
    public Result<WorkOrderResponse> close(Long id) {
        return workOrderRepository.findById(id)
                .map(wo -> {
                    if (!"COMPLETED".equals(wo.getStatus())) {
                        return Result.<WorkOrderResponse>error("当前状态不允许关闭");
                    }
                    wo.setStatus("CLOSED");

                    WorkOrder saved = workOrderRepository.save(wo);

                    log.info("工单已关闭: orderNumber={}", saved.getOrderNumber());

                    return Result.success(toResponse(saved));
                })
                .orElseGet(() -> Result.error("工单不存在"));
    }

    private String generateOrderNumber() {
        String prefix = "WO" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = workOrderRepository.count() + 1;
        return prefix + String.format("%04d", count);
    }

    private WorkOrderResponse toResponse(WorkOrder wo) {
        WorkOrderResponse response = new WorkOrderResponse();

        response.setId(wo.getId());
        response.setOrderNumber(wo.getOrderNumber());
        response.setTitle(wo.getTitle());
        response.setDescription(wo.getDescription());
        response.setEquipmentId(wo.getEquipmentId());
        response.setLocation(wo.getLocation());

        // ✅ 新增：返回类型
        response.setOrderType(wo.getOrderType());

        response.setCategory(wo.getCategory());
        response.setPriority(wo.getPriority());
        response.setStatus(wo.getStatus());
        response.setReporterId(wo.getReporterId());
        response.setHandlerId(wo.getHandlerId());
        response.setCreatedTime(wo.getCreatedTime());
        response.setUpdatedTime(wo.getUpdatedTime());

        return response;
    }
}