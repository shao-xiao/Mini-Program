package com.dehui.property.modules.workorder.service;

import com.dehui.property.common.ExcelExportUtil;
import com.dehui.property.common.OperationDict;
import com.dehui.property.common.Result;
import com.dehui.property.modules.attachment.dto.AttachmentResponse;
import com.dehui.property.modules.attachment.service.AttachmentService;
import com.dehui.property.modules.bill.entity.Bill;
import com.dehui.property.modules.bill.repository.BillRepository;
import com.dehui.property.modules.equipment.repository.EquipmentRepository;
import com.dehui.property.modules.system.entity.SysRole;
import com.dehui.property.modules.system.entity.SysUser;
import com.dehui.property.modules.system.entity.UserRole;
import com.dehui.property.modules.system.repository.SysRoleRepository;
import com.dehui.property.modules.system.repository.SysUserRepository;
import com.dehui.property.modules.system.repository.UserRoleRepository;
import com.dehui.property.modules.tenant.repository.TenantRepository;
import com.dehui.property.modules.workorder.dto.AssignableUserResponse;
import com.dehui.property.modules.workorder.dto.WorkOrderAssignRequest;
import com.dehui.property.modules.workorder.dto.WorkOrderCompleteRequest;
import com.dehui.property.modules.workorder.dto.WorkOrderCreateRequest;
import com.dehui.property.modules.workorder.dto.WorkOrderLogResponse;
import com.dehui.property.modules.workorder.dto.WorkOrderResponse;
import com.dehui.property.modules.workorder.dto.WorkOrderStatusRequest;
import com.dehui.property.modules.workorder.entity.WorkOrder;
import com.dehui.property.modules.workorder.entity.WorkOrderLog;
import com.dehui.property.modules.workorder.repository.WorkOrderLogRepository;
import com.dehui.property.modules.workorder.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderService {
    private static final int URGENT_RESPONSE_SLA_MINUTES = 30;
    private static final List<String> ASSIGNABLE_ROLE_CODES = List.of(
            "MAINTENANCE",
            "REPAIR",
            "ENGINEER",
            "ENGINEERING",
            "PROPERTY",
            "STAFF"
    );

    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderLogRepository workOrderLogRepository;
    private final EquipmentRepository equipmentRepository;
    private final SysUserRepository sysUserRepository;
    private final SysRoleRepository sysRoleRepository;
    private final UserRoleRepository userRoleRepository;
    private final BillRepository billRepository;
    private final TenantRepository tenantRepository;
    private final AttachmentService attachmentService;

    public Result<List<WorkOrderResponse>> findAll(String orderNumber, String title, String location, String category,
                                                   String priority, String status, String reporter, Long tenantId,
                                                   Long handlerId, String source, LocalDateTime submittedStart,
                                                   LocalDateTime submittedEnd) {
        List<WorkOrderResponse> responses = filtered(orderNumber, title, location, category, priority, status,
                        reporter, tenantId, handlerId, source, submittedStart, submittedEnd)
                .map(item -> toResponse(item, false))
                .toList();
        return Result.success(responses);
    }

    public Result<List<WorkOrderResponse>> findAll() {
        return findAll(null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public Result<WorkOrderResponse> findById(Long id) {
        return workOrderRepository.findById(id)
                .filter(item -> item.getDeletedAt() == null)
                .map(wo -> Result.success(toResponse(wo, true)))
                .orElseGet(() -> Result.error("工单不存在"));
    }

    public List<AssignableUserResponse> listAssignableUsers() {
        return sysUserRepository.findAll()
                .stream()
                .filter(user -> "ENGINEER".equals(user.getUserType()) || isLegacyAssignableUser(user))
                .filter(user -> "ACTIVE".equals(user.getStatus()) || "ENABLED".equals(user.getStatus()))
                .map(user -> {
                    List<SysRole> roles = userRoleRepository.findByUserId(user.getId())
                            .stream()
                            .map(UserRole::getRoleId)
                            .map(sysRoleRepository::findById)
                            .filter(java.util.Optional::isPresent)
                            .map(java.util.Optional::get)
                            .filter(role -> "ACTIVE".equals(role.getStatus()))
                            .toList();
                    return new AssignableUserResponse(
                            user.getId(),
                            user.getUsername(),
                            user.getRealName(),
                            user.getPhone(),
                            roles.stream().map(SysRole::getRoleCode).toList(),
                            roles.stream().map(SysRole::getRoleName).toList()
                    );
                })
                .filter(this::isAssignableWorker)
                .toList();
    }

    @Transactional
    public Result<WorkOrderResponse> create(WorkOrderCreateRequest request) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setOrderNumber(generateOrderNumber());
        applyCreateOrUpdate(workOrder, request);
        workOrder.setStatus("PENDING_ASSIGN");
        workOrder.setBillable(false);
        workOrder.setSource(isBlank(request.getSource()) ? "ADMIN" : request.getSource().trim().toUpperCase(Locale.ROOT));
        workOrder.setSubmittedTime(LocalDateTime.now());

        WorkOrder saved = workOrderRepository.save(workOrder);
        writeLog(saved, "CREATE", null, saved.getStatus(), "system", "新增工单");
        log.info("工单已创建: orderNumber={}, title={}", saved.getOrderNumber(), saved.getTitle());
        return Result.success(toResponse(saved, true));
    }

    @Transactional
    public Result<WorkOrderResponse> update(Long id, WorkOrderCreateRequest request) {
        return workOrderRepository.findById(id)
                .filter(item -> item.getDeletedAt() == null)
                .map(workOrder -> {
                    applyCreateOrUpdate(workOrder, request);
                    WorkOrder saved = workOrderRepository.save(workOrder);
                    writeLog(saved, "UPDATE", saved.getStatus(), saved.getStatus(), "system", "编辑工单");
                    return Result.success(toResponse(saved, true));
                })
                .orElseGet(() -> Result.error("工单不存在"));
    }

    @Transactional
    public Result<WorkOrderResponse> assign(Long id, WorkOrderAssignRequest request) {
        return workOrderRepository.findById(id)
                .filter(item -> item.getDeletedAt() == null)
                .map(wo -> {
                    String current = OperationDict.workOrderStatus(wo.getStatus());
                    if (!"PENDING_ASSIGN".equals(current) && !"ASSIGNED".equals(current)) {
                        return Result.<WorkOrderResponse>error("当前状态不允许指派");
                    }
                    String oldStatus = wo.getStatus();
                    wo.setHandlerId(request.getHandlerId());
                    wo.setStatus("ASSIGNED");
                    wo.setAssignedTime(LocalDateTime.now());
                    WorkOrder saved = workOrderRepository.save(wo);
                    writeLog(saved, "ASSIGN", oldStatus, saved.getStatus(), "system", "派单给用户ID " + request.getHandlerId());
                    return Result.success(toResponse(saved, true));
                })
                .orElseGet(() -> Result.error("工单不存在"));
    }

    @Transactional
    public Result<WorkOrderResponse> start(Long id) {
        return updateStatus(id, "PROCESSING", "system", "开始处理");
    }

    @Transactional
    public Result<WorkOrderResponse> complete(Long id, WorkOrderCompleteRequest request) {
        return workOrderRepository.findById(id)
                .filter(item -> item.getDeletedAt() == null)
                .map(wo -> {
                    String current = OperationDict.workOrderStatus(wo.getStatus());
                    if (!"PROCESSING".equals(current) && !"ASSIGNED".equals(current)) {
                        return Result.<WorkOrderResponse>error("当前状态不允许完成");
                    }
                    String oldStatus = wo.getStatus();
                    wo.setStatus("PENDING_CONFIRM");
                    wo.setCompletedTime(LocalDateTime.now());
                    if (request != null && !isBlank(request.getHandlingResult())) {
                        wo.setHandlingResult(request.getHandlingResult());
                    }
                    if (request != null) {
                        Result<Void> billingValidation = applyBillingFields(wo, request);
                        if (billingValidation.getCode() != 200) {
                            return Result.<WorkOrderResponse>error(billingValidation.getMessage());
                        }
                    }

                    WorkOrder saved = workOrderRepository.save(wo);
                    if (wo.getEquipmentId() != null) {
                        equipmentRepository.findById(wo.getEquipmentId()).ifPresent(equipment -> {
                            equipment.setStatus("NORMAL");
                            equipmentRepository.save(equipment);
                        });
                    }
                    writeLog(saved, "COMPLETE", oldStatus, saved.getStatus(), request == null ? "system" : request.getOperator(), "处理完成，待用户确认");
                    return Result.success(toResponse(saved, true));
                })
                .orElseGet(() -> Result.error("工单不存在"));
    }

    @Transactional
    public Result<WorkOrderResponse> confirm(Long id, String operator) {
        return workOrderRepository.findById(id)
                .filter(item -> item.getDeletedAt() == null)
                .map(wo -> {
                    String current = OperationDict.workOrderStatus(wo.getStatus());
                    if (!"PENDING_CONFIRM".equals(current) && !"COMPLETED".equals(current)) {
                        return Result.<WorkOrderResponse>error("当前状态不允许确认完成");
                    }
                    String oldStatus = wo.getStatus();
                    wo.setStatus("COMPLETED");
                    wo.setConfirmedTime(LocalDateTime.now());
                    WorkOrder saved = workOrderRepository.save(wo);
                    writeLog(saved, "CONFIRM", oldStatus, saved.getStatus(), operator, "确认完成");
                    return Result.success(toResponse(saved, true));
                })
                .orElseGet(() -> Result.error("工单不存在"));
    }

    @Transactional
    public Result<WorkOrderResponse> changeStatus(Long id, WorkOrderStatusRequest request) {
        return updateStatus(id, request.getStatus(), request.getOperator(), request.getRemark());
    }

    @Transactional
    public Result<WorkOrderResponse> generateBill(Long id) {
        return workOrderRepository.findById(id)
                .filter(item -> item.getDeletedAt() == null)
                .map(wo -> {
                    String current = OperationDict.workOrderStatus(wo.getStatus());
                    if (!"COMPLETED".equals(current) && !"CLOSED".equals(current) && !"PENDING_CONFIRM".equals(current)) {
                        return Result.<WorkOrderResponse>error("工单完成后才能生成账单");
                    }
                    if (!Boolean.TRUE.equals(wo.getBillable())) {
                        return Result.<WorkOrderResponse>error("该工单未标记为向租户收费");
                    }
                    if (wo.getTenantId() == null) {
                        return Result.<WorkOrderResponse>error("工单未关联租户，无法生成账单");
                    }
                    if (wo.getChargeAmount() == null || wo.getChargeAmount().compareTo(BigDecimal.ZERO) <= 0) {
                        return Result.<WorkOrderResponse>error("收费金额必须大于0");
                    }
                    if (wo.getBillId() != null) {
                        return Result.<WorkOrderResponse>error("该工单已生成账单");
                    }

                    Bill bill = new Bill();
                    bill.setBillNumber("WO-" + wo.getOrderNumber());
                    bill.setTenantId(wo.getTenantId());
                    bill.setBillType("WORK_ORDER");
                    bill.setTitle("工单服务费 - " + wo.getOrderNumber());
                    LocalDate today = LocalDate.now();
                    bill.setPeriodStart(today);
                    bill.setPeriodEnd(today);
                    bill.setAmount(wo.getChargeAmount());
                    bill.setPaidAmount(BigDecimal.ZERO);
                    bill.setDueDate(today);
                    bill.setStatus("UNPAID");
                    bill.setAuditStatus("PENDING");
                    bill.setSourceType("WORK_ORDER");
                    bill.setSourceId(wo.getId());
                    bill.setRemark(wo.getChargeRemark());

                    Bill savedBill = billRepository.save(bill);
                    wo.setBillId(savedBill.getId());
                    WorkOrder saved = workOrderRepository.save(wo);
                    writeLog(saved, "GENERATE_BILL", saved.getStatus(), saved.getStatus(), "system", "生成账单ID " + savedBill.getId());
                    return Result.success(toResponse(saved, true));
                })
                .orElseGet(() -> Result.error("工单不存在"));
    }

    @Transactional
    public Result<WorkOrderResponse> close(Long id) {
        return updateStatus(id, "CLOSED", "system", "关闭工单");
    }

    @Transactional
    public Result<WorkOrderResponse> withdraw(Long id, Long mobileUserId, String operator) {
        return workOrderRepository.findById(id)
                .filter(item -> item.getDeletedAt() == null)
                .map(wo -> {
                    if (mobileUserId != null && !Objects.equals(wo.getMobileUserId(), mobileUserId)) {
                        return Result.<WorkOrderResponse>error(403, "不能撤回他人的报修");
                    }
                    String current = OperationDict.workOrderStatus(wo.getStatus());
                    if (!"PENDING_ASSIGN".equals(current)) {
                        return Result.<WorkOrderResponse>error("工单已派单或处理中，不能撤回");
                    }
                    String oldStatus = wo.getStatus();
                    wo.setStatus("WITHDRAWN");
                    wo.setCancelledTime(LocalDateTime.now());
                    WorkOrder saved = workOrderRepository.save(wo);
                    writeLog(saved, "WITHDRAW", oldStatus, saved.getStatus(), operator, "撤回工单");
                    return Result.success(toResponse(saved, true));
                })
                .orElseGet(() -> Result.error("工单不存在"));
    }

    @Transactional
    public Result<WorkOrderResponse> evaluate(Long id, Long mobileUserId, Integer rating, String content, String operator) {
        return workOrderRepository.findById(id)
                .filter(item -> item.getDeletedAt() == null)
                .map(wo -> {
                    if (mobileUserId != null && !Objects.equals(wo.getMobileUserId(), mobileUserId)) {
                        return Result.<WorkOrderResponse>error(403, "不能评价他人的报修");
                    }
                    String current = OperationDict.workOrderStatus(wo.getStatus());
                    if (!"COMPLETED".equals(current) && !"CLOSED".equals(current)) {
                        return Result.<WorkOrderResponse>error("工单确认完成后才能评价");
                    }
                    if (wo.getRating() != null) {
                        return Result.<WorkOrderResponse>error("该工单已评价");
                    }
                    wo.setRating(rating);
                    wo.setEvaluationContent(content);
                    wo.setEvaluationTime(LocalDateTime.now());
                    WorkOrder saved = workOrderRepository.save(wo);
                    writeLog(saved, "EVALUATE", saved.getStatus(), saved.getStatus(), operator, "评价工单");
                    return Result.success(toResponse(saved, true));
                })
                .orElseGet(() -> Result.error("工单不存在"));
    }

    public byte[] export(String orderNumber, String title, String location, String category, String priority, String status,
                         String reporter, Long tenantId, Long handlerId, String source, LocalDateTime submittedStart,
                         LocalDateTime submittedEnd) {
        List<String> headers = List.of("工单号", "标题", "位置", "问题类别", "优先级", "状态", "报修人", "租户", "处理人", "来源", "提交时间", "完成时间");
        List<List<Object>> rows = filtered(orderNumber, title, location, category, priority, status, reporter,
                        tenantId, handlerId, source, submittedStart, submittedEnd)
                .map(item -> {
                    WorkOrderResponse response = toResponse(item, false);
                    return List.<Object>of(
                            safe(response.getOrderNumber()),
                            safe(response.getTitle()),
                            safe(response.getLocation()),
                            safe(response.getCategoryLabel()),
                            safe(response.getPriorityLabel()),
                            safe(response.getStatusLabel()),
                            safe(response.getReporterName()),
                            safe(response.getTenantName()),
                            safe(response.getHandlerName()),
                            safe(response.getSourceLabel()),
                            response.getSubmittedTime() == null ? "" : response.getSubmittedTime(),
                            response.getCompletedTime() == null ? "" : response.getCompletedTime()
                    );
                })
                .toList();
        return ExcelExportUtil.export("工单管理", headers, rows);
    }

    public Result<WorkOrderResponse> createFromInspection(Long inspectionRecordId, String title, String location, String description) {
        WorkOrderCreateRequest request = new WorkOrderCreateRequest();
        request.setTitle(isBlank(title) ? "巡检异常转工单" : title);
        request.setDescription(description);
        request.setLocation(location);
        request.setOrderType("PATROL");
        request.setCategory("SAFETY_RISK");
        request.setPriority("HIGH");
        request.setSource("INSPECTION");
        request.setInspectionRecordId(inspectionRecordId);
        return create(request);
    }

    private Stream<WorkOrder> filtered(String orderNumber, String title, String location, String category,
                                       String priority, String status, String reporter, Long tenantId,
                                       Long handlerId, String source, LocalDateTime submittedStart,
                                       LocalDateTime submittedEnd) {
        String normalizedStatus = isBlank(status) ? null : OperationDict.workOrderStatus(status);
        return workOrderRepository.findAll()
                .stream()
                .filter(item -> item.getDeletedAt() == null)
                .filter(item -> contains(item.getOrderNumber(), orderNumber))
                .filter(item -> contains(item.getTitle(), title))
                .filter(item -> contains(item.getLocation(), location))
                .filter(item -> isBlank(category) || category.equals(item.getCategory()))
                .filter(item -> isBlank(priority) || priority.equals(item.getPriority()))
                .filter(item -> normalizedStatus == null || normalizedStatus.equals(OperationDict.workOrderStatus(item.getStatus())))
                .filter(item -> contains(item.getReporterName(), reporter)
                        || contains(item.getReporterPhone(), reporter)
                        || contains(String.valueOf(item.getReporterId()), reporter))
                .filter(item -> tenantId == null || tenantId.equals(item.getTenantId()))
                .filter(item -> handlerId == null || handlerId.equals(item.getHandlerId()))
                .filter(item -> isBlank(source) || source.equals(item.getSource()))
                .filter(item -> {
                    LocalDateTime submitted = item.getSubmittedTime() == null ? item.getCreatedTime() : item.getSubmittedTime();
                    return submittedStart == null || (submitted != null && !submitted.isBefore(submittedStart));
                })
                .filter(item -> {
                    LocalDateTime submitted = item.getSubmittedTime() == null ? item.getCreatedTime() : item.getSubmittedTime();
                    return submittedEnd == null || (submitted != null && !submitted.isAfter(submittedEnd));
                })
                .sorted((a, b) -> {
                    LocalDateTime at = a.getSubmittedTime() == null ? a.getCreatedTime() : a.getSubmittedTime();
                    LocalDateTime bt = b.getSubmittedTime() == null ? b.getCreatedTime() : b.getSubmittedTime();
                    if (at == null && bt == null) {
                        return 0;
                    }
                    if (at == null) {
                        return 1;
                    }
                    if (bt == null) {
                        return -1;
                    }
                    return bt.compareTo(at);
                });
    }

    private void applyCreateOrUpdate(WorkOrder workOrder, WorkOrderCreateRequest request) {
        workOrder.setTitle(request.getTitle());
        workOrder.setDescription(request.getDescription());
        workOrder.setEquipmentId(request.getEquipmentId());
        workOrder.setLocation(request.getLocation());
        workOrder.setOrderType(isBlank(request.getOrderType()) ? "REPAIR" : request.getOrderType());
        workOrder.setCategory(request.getCategory());
        workOrder.setPriority(isBlank(request.getPriority()) ? "NORMAL" : request.getPriority());
        workOrder.setReporterId(request.getReporterId());
        workOrder.setTenantId(request.getTenantId());
        workOrder.setReporterName(request.getReporterName());
        workOrder.setReporterPhone(request.getReporterPhone());
        workOrder.setRemark(request.getRemark());
        workOrder.setInspectionRecordId(request.getInspectionRecordId());
    }

    private Result<Void> applyBillingFields(WorkOrder wo, WorkOrderCompleteRequest request) {
        boolean billable = Boolean.TRUE.equals(request.getBillable());
        if (billable && wo.getTenantId() == null) {
            return Result.error("向租户收费的工单需先关联租户");
        }
        if (billable && (request.getChargeAmount() == null || request.getChargeAmount().compareTo(BigDecimal.ZERO) <= 0)) {
            return Result.error("收费金额必须大于0");
        }
        wo.setBillable(billable);
        wo.setChargeAmount(billable ? request.getChargeAmount() : null);
        wo.setChargeRemark(billable ? request.getChargeRemark() : null);
        return Result.success();
    }

    private Result<WorkOrderResponse> updateStatus(Long id, String status, String operator, String remark) {
        if (isBlank(status)) {
            return Result.error("状态不能为空");
        }
        return workOrderRepository.findById(id)
                .filter(item -> item.getDeletedAt() == null)
                .map(wo -> {
                    String oldStatus = wo.getStatus();
                    String normalized = OperationDict.workOrderStatus(status);
                    wo.setStatus(normalized);
                    LocalDateTime now = LocalDateTime.now();
                    if ("PROCESSING".equals(normalized) && wo.getProcessingTime() == null) {
                        wo.setProcessingTime(now);
                    }
                    if ("PENDING_CONFIRM".equals(normalized) && wo.getCompletedTime() == null) {
                        wo.setCompletedTime(now);
                    }
                    if ("COMPLETED".equals(normalized) && wo.getConfirmedTime() == null) {
                        wo.setConfirmedTime(now);
                    }
                    if ("CLOSED".equals(normalized) && wo.getClosedTime() == null) {
                        wo.setClosedTime(now);
                    }
                    if ("WITHDRAWN".equals(normalized) && wo.getCancelledTime() == null) {
                        wo.setCancelledTime(now);
                    }
                    WorkOrder saved = workOrderRepository.save(wo);
                    writeLog(saved, "STATUS_CHANGE", oldStatus, saved.getStatus(), operator, isBlank(remark) ? "状态变更" : remark);
                    return Result.success(toResponse(saved, true));
                })
                .orElseGet(() -> Result.error("工单不存在"));
    }

    private String generateOrderNumber() {
        String prefix = "WO-M-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
        long count = workOrderRepository.countByOrderNumberStartingWith(prefix) + 1;
        String candidate = prefix + String.format("%04d", count);
        while (workOrderRepository.existsByOrderNumber(candidate)) {
            count++;
            candidate = prefix + String.format("%04d", count);
        }
        return candidate;
    }

    private WorkOrderResponse toResponse(WorkOrder wo, boolean detail) {
        WorkOrderResponse response = new WorkOrderResponse();
        response.setId(wo.getId());
        response.setOrderNumber(wo.getOrderNumber());
        response.setTitle(wo.getTitle());
        response.setDescription(wo.getDescription());
        response.setEquipmentId(wo.getEquipmentId());
        response.setLocation(wo.getLocation());
        response.setOrderType(wo.getOrderType());
        response.setOrderTypeLabel(orderTypeText(wo.getOrderType()));
        response.setCategory(wo.getCategory());
        response.setCategoryLabel(categoryText(wo.getCategory()));
        response.setPriority(wo.getPriority());
        response.setPriorityLabel(priorityText(wo.getPriority()));
        response.setStatus(OperationDict.workOrderStatus(wo.getStatus()));
        response.setStatusLabel(OperationDict.workOrderStatusLabel(wo.getStatus()));
        response.setSource(isBlank(wo.getSource()) ? (wo.getMobileUserId() == null ? "ADMIN" : "MINIPROGRAM") : wo.getSource());
        response.setSourceLabel(sourceText(response.getSource()));
        response.setSlaOverdue(isUrgentResponseOverdue(wo));
        response.setSlaLabel(resolveSlaLabel(wo));
        response.setReporterId(wo.getReporterId());
        response.setMobileUserId(wo.getMobileUserId());
        response.setTenantId(wo.getTenantId());
        if (wo.getTenantId() != null) {
            tenantRepository.findById(wo.getTenantId()).ifPresent(tenant -> response.setTenantName(tenant.getTenantName()));
        }
        response.setReporterName(wo.getReporterName());
        response.setReporterPhone(wo.getReporterPhone());
        response.setImageUrls(parseImageUrls(wo.getImageUrls()));
        response.setReportAttachments(mergeLegacyReportAttachments(wo));
        response.setBeforeAttachments(attachmentService.listByCategory("WORK_ORDER", wo.getId(), "BEFORE"));
        response.setAfterAttachments(attachmentService.listByCategory("WORK_ORDER", wo.getId(), "AFTER"));
        response.setHandlingResult(wo.getHandlingResult());
        response.setBillable(wo.getBillable());
        response.setChargeAmount(wo.getChargeAmount());
        response.setChargeRemark(wo.getChargeRemark());
        response.setBillId(wo.getBillId());
        response.setRating(wo.getRating());
        response.setEvaluationContent(wo.getEvaluationContent());
        response.setEvaluationTime(wo.getEvaluationTime());
        response.setHandlerId(wo.getHandlerId());
        if (wo.getHandlerId() != null) {
            sysUserRepository.findById(wo.getHandlerId()).ifPresent(user -> response.setHandlerName(user.getRealName() == null ? user.getUsername() : user.getRealName()));
        }
        response.setInspectionRecordId(wo.getInspectionRecordId());
        response.setRemark(wo.getRemark());
        response.setSubmittedTime(wo.getSubmittedTime());
        response.setAssignedTime(wo.getAssignedTime());
        response.setProcessingTime(wo.getProcessingTime());
        response.setCompletedTime(wo.getCompletedTime());
        response.setClosedTime(wo.getClosedTime());
        response.setCancelledTime(wo.getCancelledTime());
        response.setConfirmedTime(wo.getConfirmedTime());
        response.setCreatedTime(wo.getCreatedTime());
        response.setUpdatedTime(wo.getUpdatedTime());
        if (detail) {
            response.setLogs(workOrderLogRepository.findByWorkOrderIdOrderByCreatedTimeAsc(wo.getId())
                    .stream()
                    .map(this::toLogResponse)
                    .toList());
        }
        return response;
    }

    private List<AttachmentResponse> mergeLegacyReportAttachments(WorkOrder wo) {
        List<AttachmentResponse> attachments = attachmentService.listByCategory("WORK_ORDER", wo.getId(), "REPORT");
        return attachments.isEmpty() && !parseImageUrls(wo.getImageUrls()).isEmpty()
                ? Collections.emptyList()
                : attachments;
    }

    private WorkOrderLogResponse toLogResponse(WorkOrderLog log) {
        WorkOrderLogResponse response = new WorkOrderLogResponse();
        response.setId(log.getId());
        response.setWorkOrderId(log.getWorkOrderId());
        response.setOperationType(log.getOperationType());
        response.setOldStatus(OperationDict.workOrderStatus(log.getOldStatus()));
        response.setOldStatusLabel(OperationDict.workOrderStatusLabel(log.getOldStatus()));
        response.setNewStatus(OperationDict.workOrderStatus(log.getNewStatus()));
        response.setNewStatusLabel(OperationDict.workOrderStatusLabel(log.getNewStatus()));
        response.setOperator(log.getOperator());
        response.setContent(log.getContent());
        response.setCreatedTime(log.getCreatedTime());
        return response;
    }

    private void writeLog(WorkOrder workOrder, String type, String oldStatus, String newStatus, String operator, String content) {
        WorkOrderLog log = new WorkOrderLog();
        log.setWorkOrderId(workOrder.getId());
        log.setOperationType(type);
        log.setOldStatus(oldStatus);
        log.setNewStatus(newStatus);
        log.setOperator(isBlank(operator) ? "system" : operator);
        log.setContent(content);
        workOrderLogRepository.save(log);
    }

    private List<String> parseImageUrls(String imageUrls) {
        if (imageUrls == null || imageUrls.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(imageUrls.split(",")).map(String::trim).filter(url -> !url.isBlank()).toList();
    }

    private boolean isUrgentResponseOverdue(WorkOrder wo) {
        String status = OperationDict.workOrderStatus(wo.getStatus());
        if (!"URGENT".equals(wo.getPriority()) || !"PENDING_ASSIGN".equals(status)) {
            return false;
        }
        LocalDateTime submittedTime = wo.getSubmittedTime() != null ? wo.getSubmittedTime() : wo.getCreatedTime();
        return submittedTime != null && Duration.between(submittedTime, LocalDateTime.now()).toMinutes() >= URGENT_RESPONSE_SLA_MINUTES;
    }

    private String resolveSlaLabel(WorkOrder wo) {
        if (isUrgentResponseOverdue(wo)) {
            return "紧急工单超30分钟未响应";
        }
        if ("URGENT".equals(wo.getPriority()) && "PENDING_ASSIGN".equals(OperationDict.workOrderStatus(wo.getStatus()))) {
            return "紧急工单需30分钟内派单";
        }
        return null;
    }

    private boolean isAssignableWorker(AssignableUserResponse user) {
        boolean roleCodeMatched = user.getRoleCodes().stream().anyMatch(ASSIGNABLE_ROLE_CODES::contains);
        boolean roleNameMatched = user.getRoleNames().stream()
                .anyMatch(name -> name != null && (name.contains("维修") || name.contains("工程") || name.contains("物业")));
        return roleCodeMatched || roleNameMatched;
    }

    private boolean isLegacyAssignableUser(SysUser user) {
        return userRoleRepository.findByUserId(user.getId())
                .stream()
                .map(UserRole::getRoleId)
                .map(sysRoleRepository::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .anyMatch(role -> ASSIGNABLE_ROLE_CODES.contains(role.getRoleCode())
                        || "ENGINEER".equals(role.getRoleCode())
                        || (role.getRoleName() != null && (role.getRoleName().contains("维修") || role.getRoleName().contains("工程"))));
    }

    private String orderTypeText(String value) {
        return switch (safe(value)) {
            case "REPAIR" -> "维修报修";
            case "PATROL" -> "巡检任务";
            case "CLEAN" -> "保洁服务";
            case "SECURITY" -> "安保事件";
            default -> safe(value);
        };
    }

    private String categoryText(String value) {
        return switch (safe(value)) {
            case "WATER", "WATER_ELECTRIC" -> "水电维修";
            case "ELECTRIC" -> "电力";
            case "AIR_CONDITIONER", "HVAC" -> "空调暖通";
            case "DOOR_WINDOW" -> "门窗";
            case "NETWORK", "ACCESS_NETWORK" -> "门禁网络";
            case "CLEANING", "PUBLIC_CLEAN" -> "保洁";
            case "FIRE_PATROL" -> "消防巡检";
            case "PUBLIC_AREA_PATROL" -> "公共区域巡查";
            case "SAFETY_RISK" -> "安全隐患";
            default -> isBlank(value) ? "未分类" : value;
        };
    }

    private String priorityText(String value) {
        return switch (safe(value)) {
            case "LOW" -> "低";
            case "HIGH" -> "高";
            case "URGENT" -> "紧急";
            case "MEDIUM" -> "中";
            default -> "普通";
        };
    }

    private String sourceText(String value) {
        return switch (safe(value)) {
            case "MINIPROGRAM" -> "小程序";
            case "INSPECTION" -> "巡检转工单";
            default -> "后台";
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
