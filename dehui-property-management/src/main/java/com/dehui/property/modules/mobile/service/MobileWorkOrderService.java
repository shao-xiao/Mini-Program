package com.dehui.property.modules.mobile.service;

import com.dehui.property.common.Result;
import com.dehui.property.modules.attachment.dto.AttachmentResponse;
import com.dehui.property.modules.attachment.service.AttachmentService;
import com.dehui.property.modules.mobile.dto.MobileUserProfile;
import com.dehui.property.modules.mobile.dto.MobileWorkOrderEvaluationRequest;
import com.dehui.property.modules.mobile.dto.MobileWorkOrderHomeResponse;
import com.dehui.property.modules.mobile.dto.MobileWorkOrderRequest;
import com.dehui.property.modules.mobile.dto.MobileWorkOrderResponse;
import com.dehui.property.modules.workorder.entity.WorkOrder;
import com.dehui.property.modules.workorder.repository.WorkOrderRepository;
import com.dehui.property.modules.workorder.service.WorkOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class MobileWorkOrderService {

    private static final int MAX_WORK_ORDER_IMAGE_COUNT = 6;

    private final MobileAuthService mobileAuthService;
    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderService workOrderService;
    private final AttachmentService attachmentService;

    public Result<MobileWorkOrderHomeResponse> home(String token) {
        MobileUserProfile profile = mobileAuthService.getProfile(token);
        if (profile == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        List<MobileWorkOrderResponse> workOrders = workOrderRepository.findByMobileUserIdOrderByCreatedTimeDesc(profile.getId())
                .stream()
                .map(this::toResponse)
                .toList();
        return Result.success(new MobileWorkOrderHomeResponse(profile, workOrders));
    }

    public Result<MobileWorkOrderResponse> detail(String token, Long id) {
        MobileUserProfile profile = mobileAuthService.getProfile(token);
        if (profile == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        WorkOrder workOrder = workOrderRepository.findById(id).orElse(null);
        if (workOrder == null || workOrder.getDeletedAt() != null) {
            return Result.error("报修工单不存在");
        }
        if (workOrder.getMobileUserId() == null || !workOrder.getMobileUserId().equals(profile.getId())) {
            return Result.error(403, "不能查看他人的报修");
        }
        return Result.success(toResponse(workOrder));
    }

    @Transactional
    public Result<MobileWorkOrderResponse> create(String token, MobileWorkOrderRequest request) {
        MobileUserProfile profile = mobileAuthService.getProfile(token);
        if (profile == null) {
            return Result.error(401, "未登录或登录已过期");
        }

        WorkOrder workOrder = new WorkOrder();
        workOrder.setOrderNumber(generateOrderNumber());
        workOrder.setTitle(request.getTitle());
        workOrder.setDescription(request.getDescription());
        workOrder.setLocation(request.getLocation());
        workOrder.setOrderType("REPAIR");
        workOrder.setCategory(request.getCategory());
        workOrder.setPriority(request.getPriority() == null || request.getPriority().isBlank() ? "NORMAL" : request.getPriority());
        workOrder.setStatus("PENDING_ASSIGN");
        workOrder.setMobileUserId(profile.getId());
        workOrder.setTenantId(profile.getBoundTenantId());
        workOrder.setReporterId(profile.getBoundSysUserId());
        workOrder.setReporterName(resolveReporterName(profile));
        workOrder.setReporterPhone(request.getContactPhone() == null || request.getContactPhone().isBlank()
                ? profile.getPhone()
                : request.getContactPhone());
        workOrder.setSubmittedTime(LocalDateTime.now());
        workOrder.setSource("MINIPROGRAM");

        return Result.success(toResponse(workOrderRepository.save(workOrder)));
    }

    @Transactional
    public Result<MobileWorkOrderResponse> cancel(String token, Long id) {
        MobileUserProfile profile = mobileAuthService.getProfile(token);
        if (profile == null) {
            return Result.error(401, "未登录或登录已过期");
        }

        WorkOrder workOrder = workOrderRepository.findById(id).orElse(null);
        if (workOrder == null) {
            return Result.error("报修工单不存在");
        }
        if (workOrder.getMobileUserId() == null || !workOrder.getMobileUserId().equals(profile.getId())) {
            return Result.error(403, "不能撤回他人的报修");
        }
        if (!"PENDING_ASSIGN".equals(workOrder.getStatus()) && !"CREATED".equals(workOrder.getStatus())) {
            return Result.error("工单已派单或处理中，不能撤回");
        }

        workOrder.setStatus("WITHDRAWN");
        workOrder.setCancelledTime(LocalDateTime.now());
        return Result.success(toResponse(workOrderRepository.save(workOrder)));
    }

    @Transactional
    public Result<MobileWorkOrderResponse> uploadImage(String token, Long id, MultipartFile file) {
        MobileUserProfile profile = mobileAuthService.getProfile(token);
        if (profile == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        if (file == null || file.isEmpty()) {
            return Result.error("请选择要上传的图片");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            return Result.error("单张图片不能超过5MB");
        }

        WorkOrder workOrder = workOrderRepository.findById(id).orElse(null);
        if (workOrder == null) {
            return Result.error("报修工单不存在");
        }
        if (workOrder.getMobileUserId() == null || !workOrder.getMobileUserId().equals(profile.getId())) {
            return Result.error(403, "不能给他人的报修上传图片");
        }
        if ("WITHDRAWN".equals(workOrder.getStatus()) || "CANCELLED".equals(workOrder.getStatus()) || "CLOSED".equals(workOrder.getStatus())) {
            return Result.error("当前状态不能上传图片");
        }
        int currentCount = attachmentService.listByCategory("WORK_ORDER", id, "REPORT").size() + parseImageUrls(workOrder.getImageUrls()).size();
        if (currentCount >= MAX_WORK_ORDER_IMAGE_COUNT) {
            return Result.error("每个报修最多上传" + MAX_WORK_ORDER_IMAGE_COUNT + "张图片");
        }

        String originalFilename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = resolveExtension(originalFilename, file.getContentType());
        if (!List.of(".jpg", ".jpeg", ".png", ".webp").contains(extension)) {
            return Result.error("仅支持 jpg、png、webp 图片");
        }

        Result<AttachmentResponse> uploadResult = attachmentService.upload("WORK_ORDER", id, "REPORT", String.valueOf(profile.getId()), file);
        if (uploadResult.getCode() != 200) {
            return Result.error(uploadResult.getMessage());
        }
        return Result.success(toResponse(workOrder));
    }

    @Transactional
    public Result<MobileWorkOrderResponse> confirm(String token, Long id) {
        MobileUserProfile profile = mobileAuthService.getProfile(token);
        if (profile == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        WorkOrder workOrder = workOrderRepository.findById(id).orElse(null);
        if (workOrder == null || workOrder.getDeletedAt() != null) {
            return Result.error("报修工单不存在");
        }
        if (workOrder.getMobileUserId() == null || !workOrder.getMobileUserId().equals(profile.getId())) {
            return Result.error(403, "不能确认他人的报修");
        }
        Result<com.dehui.property.modules.workorder.dto.WorkOrderResponse> result = workOrderService.confirm(id, String.valueOf(profile.getId()));
        if (result.getCode() != 200) {
            return Result.error(result.getCode(), result.getMessage());
        }
        return workOrderRepository.findById(id)
                .map(item -> Result.success(toResponse(item)))
                .orElseGet(() -> Result.error("报修工单不存在"));
    }

    @Transactional
    public Result<MobileWorkOrderResponse> evaluate(String token, Long id, MobileWorkOrderEvaluationRequest request) {
        MobileUserProfile profile = mobileAuthService.getProfile(token);
        if (profile == null) {
            return Result.error(401, "未登录或登录已过期");
        }

        WorkOrder workOrder = workOrderRepository.findById(id).orElse(null);
        if (workOrder == null) {
            return Result.error("报修工单不存在");
        }
        if (workOrder.getMobileUserId() == null || !workOrder.getMobileUserId().equals(profile.getId())) {
            return Result.error(403, "不能评价他人的报修");
        }
        if (!"COMPLETED".equals(workOrder.getStatus()) && !"CLOSED".equals(workOrder.getStatus())) {
            return Result.error("工单完成后才能评价");
        }
        if (workOrder.getRating() != null) {
            return Result.error("该工单已评价");
        }

        workOrder.setRating(request.getRating());
        workOrder.setEvaluationContent(request.getContent());
        workOrder.setEvaluationTime(LocalDateTime.now());
        return Result.success(toResponse(workOrderRepository.save(workOrder)));
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

    private String resolveReporterName(MobileUserProfile profile) {
        if (profile.getBoundSysRealName() != null && !profile.getBoundSysRealName().isBlank()) {
            return profile.getBoundSysRealName();
        }
        if (profile.getNickname() != null && !profile.getNickname().isBlank()) {
            return profile.getNickname();
        }
        if (profile.getBoundTenantName() != null && !profile.getBoundTenantName().isBlank()) {
            return profile.getBoundTenantName();
        }
        return "小程序用户";
    }

    private MobileWorkOrderResponse toResponse(WorkOrder workOrder) {
        MobileWorkOrderResponse response = new MobileWorkOrderResponse();
        response.setId(workOrder.getId());
        response.setOrderNumber(workOrder.getOrderNumber());
        response.setTitle(workOrder.getTitle());
        response.setDescription(workOrder.getDescription());
        response.setLocation(workOrder.getLocation());
        response.setOrderType(workOrder.getOrderType());
        response.setOrderTypeText(toOrderTypeText(workOrder.getOrderType()));
        response.setCategory(workOrder.getCategory());
        response.setCategoryText(toCategoryText(workOrder.getCategory()));
        response.setPriority(workOrder.getPriority());
        response.setPriorityText(toPriorityText(workOrder.getPriority()));
        response.setStatus(workOrder.getStatus());
        response.setStatusText(toStatusText(workOrder.getStatus()));
        response.setReporterName(workOrder.getReporterName());
        response.setReporterPhone(workOrder.getReporterPhone());
        response.setImageUrls(Stream.concat(
                parseImageUrls(workOrder.getImageUrls()).stream(),
                attachmentService.listByCategory("WORK_ORDER", workOrder.getId(), "REPORT").stream().map(AttachmentResponse::getFileUrl)
        ).toList());
        response.setHandlingResult(workOrder.getHandlingResult());
        response.setRating(workOrder.getRating());
        response.setEvaluationContent(workOrder.getEvaluationContent());
        response.setEvaluationTime(workOrder.getEvaluationTime());
        response.setSubmittedTime(workOrder.getSubmittedTime());
        response.setAssignedTime(workOrder.getAssignedTime());
        response.setProcessingTime(workOrder.getProcessingTime());
        response.setCompletedTime(workOrder.getCompletedTime());
        response.setClosedTime(workOrder.getClosedTime());
        response.setCancelledTime(workOrder.getCancelledTime());
        response.setCreatedTime(workOrder.getCreatedTime());
        response.setUpdatedTime(workOrder.getUpdatedTime());
        return response;
    }

    private String toOrderTypeText(String orderType) {
        if ("REPAIR".equals(orderType)) return "维修";
        if ("CLEAN".equals(orderType)) return "保洁";
        if ("SECURITY".equals(orderType)) return "安保";
        if ("PATROL".equals(orderType)) return "巡更";
        return "工单";
    }

    private String toCategoryText(String category) {
        if ("WATER".equals(category)) return "水路";
        if ("ELECTRIC".equals(category)) return "电路";
        if ("AIR_CONDITIONER".equals(category)) return "空调";
        if ("DOOR_WINDOW".equals(category)) return "门窗";
        if ("NETWORK".equals(category)) return "网络";
        if ("CLEANING".equals(category)) return "保洁";
        if ("OTHER".equals(category)) return "其他";
        return category == null || category.isBlank() ? "未分类" : category;
    }

    private String toPriorityText(String priority) {
        if ("URGENT".equals(priority)) return "紧急";
        if ("HIGH".equals(priority)) return "较高";
        if ("LOW".equals(priority)) return "较低";
        return "普通";
    }

    private String toStatusText(String status) {
        if ("PENDING_ASSIGN".equals(status) || "CREATED".equals(status)) return "待派单";
        if ("ASSIGNED".equals(status)) return "已派单";
        if ("PROCESSING".equals(status)) return "处理中";
        if ("PENDING_CONFIRM".equals(status)) return "待确认";
        if ("COMPLETED".equals(status)) return "已完成";
        if ("CLOSED".equals(status)) return "已关闭";
        if ("WITHDRAWN".equals(status) || "CANCELLED".equals(status)) return "已撤回";
        return status == null || status.isBlank() ? "未知" : status;
    }

    private String resolveExtension(String filename, String contentType) {
        String lowerName = filename.toLowerCase();
        if (lowerName.endsWith(".jpeg")) return ".jpeg";
        if (lowerName.endsWith(".jpg")) return ".jpg";
        if (lowerName.endsWith(".png")) return ".png";
        if (lowerName.endsWith(".webp")) return ".webp";
        if ("image/png".equals(contentType)) return ".png";
        if ("image/webp".equals(contentType)) return ".webp";
        return ".jpg";
    }

    private List<String> parseImageUrls(String imageUrls) {
        if (imageUrls == null || imageUrls.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(imageUrls.split(","))
                .map(String::trim)
                .filter(url -> !url.isBlank())
                .toList();
    }
}
