package com.dehui.property.modules.inspection.service;

import com.dehui.property.common.ExcelExportUtil;
import com.dehui.property.common.OperationDict;
import com.dehui.property.modules.inspection.dto.InspectionCreateRequest;
import com.dehui.property.modules.inspection.dto.InspectionPlanRequest;
import com.dehui.property.modules.inspection.entity.InspectionPlan;
import com.dehui.property.modules.inspection.entity.InspectionRecord;
import com.dehui.property.modules.inspection.repository.InspectionPlanRepository;
import com.dehui.property.modules.inspection.repository.InspectionRepository;
import com.dehui.property.modules.workorder.dto.WorkOrderResponse;
import com.dehui.property.modules.workorder.service.WorkOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class InspectionService {
    private final InspectionRepository repository;
    private final InspectionPlanRepository planRepository;
    private final WorkOrderService workOrderService;

    @Transactional
    public InspectionRecord create(InspectionCreateRequest req) {
        InspectionRecord record = new InspectionRecord();
        applyRecord(record, req);
        record.setStatus(OperationDict.inspectionStatus(req.getStatus()));
        return repository.save(record);
    }

    @Transactional
    public InspectionRecord update(Long id, InspectionCreateRequest req) {
        InspectionRecord record = get(id);
        applyRecord(record, req);
        if (req.getStatus() != null) {
            record.setStatus(OperationDict.inspectionStatus(req.getStatus()));
        }
        return repository.save(record);
    }

    public List<InspectionRecord> list(String inspectionType, String status, String result, String inspector,
                                       String area, LocalDate startDate, LocalDate endDate) {
        String normalizedStatus = status == null || status.isBlank() ? null : OperationDict.inspectionStatus(status);
        String normalizedResult = result == null || result.isBlank() ? null : OperationDict.inspectionResult(result);
        return repository.findByDeletedAtIsNullOrderByInspectionDateDescCreatedTimeDesc()
                .stream()
                .filter(item -> isBlank(inspectionType) || inspectionType.equals(item.getInspectionType()))
                .filter(item -> normalizedStatus == null || normalizedStatus.equals(OperationDict.inspectionStatus(item.getStatus())))
                .filter(item -> normalizedResult == null || normalizedResult.equals(OperationDict.inspectionResult(item.getResult())))
                .filter(item -> contains(item.getInspector(), inspector))
                .filter(item -> contains(item.getArea(), area))
                .filter(item -> startDate == null || (item.getInspectionDate() != null && !item.getInspectionDate().isBefore(startDate)))
                .filter(item -> endDate == null || (item.getInspectionDate() != null && !item.getInspectionDate().isAfter(endDate)))
                .toList();
    }

    public List<InspectionRecord> list() {
        return list(null, null, null, null, null, null, null);
    }

    public InspectionRecord get(Long id) {
        return repository.findById(id)
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new RuntimeException("巡检记录不存在"));
    }

    @Transactional
    public InspectionRecord close(Long id) {
        InspectionRecord record = get(id);
        record.setStatus("CLOSED");
        record.setClosedTime(LocalDateTime.now());
        return repository.save(record);
    }

    @Transactional
    public WorkOrderResponse convertToWorkOrder(Long id) {
        InspectionRecord record = get(id);
        if (!"ABNORMAL".equals(OperationDict.inspectionResult(record.getResult()))) {
            throw new RuntimeException("只有异常巡检才能转工单");
        }
        if (record.getConvertedWorkOrderId() != null) {
            return workOrderService.findById(record.getConvertedWorkOrderId()).getData();
        }
        WorkOrderResponse workOrder = workOrderService.createFromInspection(
                record.getId(),
                OperationDict.inspectionTypeLabel(record.getInspectionType()) + "异常：" + safe(record.getTarget()),
                record.getArea(),
                record.getProblemDescription()
        ).getData();
        record.setConvertedWorkOrderId(workOrder.getId());
        repository.save(record);
        return workOrder;
    }

    public byte[] export(String inspectionType, String status, String result, String inspector,
                         String area, LocalDate startDate, LocalDate endDate) {
        List<String> headers = List.of("巡检日期", "巡检人", "巡检类型", "区域/地点", "巡检对象", "结果", "状态", "问题描述", "处理措施", "备注");
        List<List<Object>> rows = list(inspectionType, status, result, inspector, area, startDate, endDate)
                .stream()
                .map(item -> List.<Object>of(
                        item.getInspectionDate() == null ? "" : item.getInspectionDate(),
                        safe(item.getInspector()),
                        OperationDict.inspectionTypeLabel(item.getInspectionType()),
                        safe(item.getArea()),
                        safe(item.getTarget()),
                        OperationDict.inspectionResultLabel(item.getResult()),
                        OperationDict.inspectionStatusLabel(item.getStatus()),
                        safe(item.getProblemDescription()),
                        safe(item.getActionTaken()),
                        safe(item.getRemark())
                ))
                .toList();
        return ExcelExportUtil.export("巡检管理", headers, rows);
    }

    @Transactional
    public InspectionPlan createPlan(InspectionPlanRequest request) {
        InspectionPlan plan = new InspectionPlan();
        applyPlan(plan, request);
        plan.setStatus(isBlank(request.getStatus()) ? "NOT_STARTED" : request.getStatus());
        return planRepository.save(plan);
    }

    @Transactional
    public InspectionPlan updatePlan(Long id, InspectionPlanRequest request) {
        InspectionPlan plan = planRepository.findById(id)
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new RuntimeException("巡检计划不存在"));
        applyPlan(plan, request);
        if (!isBlank(request.getStatus())) {
            plan.setStatus(request.getStatus());
        }
        return planRepository.save(plan);
    }

    public List<InspectionPlan> listPlans() {
        return planRepository.findByDeletedAtIsNullOrderByPlannedDateDescCreatedTimeDesc();
    }

    @Transactional
    public void deletePlan(Long id) {
        InspectionPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("巡检计划不存在"));
        plan.setDeletedAt(LocalDateTime.now());
        planRepository.save(plan);
    }

    private void applyRecord(InspectionRecord record, InspectionCreateRequest req) {
        record.setInspectionDate(req.getInspectionDate());
        record.setInspector(req.getInspector());
        record.setInspectionType(normalizeType(req.getInspectionType()));
        record.setArea(req.getArea());
        record.setTarget(req.getTarget());
        record.setResult(OperationDict.inspectionResult(req.getResult()));
        record.setProblemDescription(req.getProblemDescription());
        record.setActionTaken(req.getActionTaken());
        record.setRemark(req.getRemark());
        record.setPlanId(req.getPlanId());
    }

    private void applyPlan(InspectionPlan plan, InspectionPlanRequest request) {
        plan.setPlanName(request.getPlanName());
        plan.setInspectionType(normalizeType(request.getInspectionType()));
        plan.setArea(request.getArea());
        plan.setTarget(request.getTarget());
        plan.setInspector(request.getInspector());
        plan.setPlannedDate(request.getPlannedDate());
        plan.setRemark(request.getRemark());
    }

    private String normalizeType(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "消防巡检", "FIRE_PATROL" -> "FIRE";
            case "安防巡检" -> "SECURITY";
            case "弱电巡检" -> "WEAK_CURRENT";
            case "空调巡检" -> "HVAC";
            case "电梯巡检" -> "ELEVATOR";
            case "公共设施巡检" -> "PUBLIC_FACILITY";
            case "环境卫生巡检" -> "ENVIRONMENT";
            default -> normalized;
        };
    }

    private boolean contains(String source, String keyword) {
        if (isBlank(keyword)) {
            return true;
        }
        return source != null && source.toLowerCase(Locale.ROOT).contains(keyword.trim().toLowerCase(Locale.ROOT));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
