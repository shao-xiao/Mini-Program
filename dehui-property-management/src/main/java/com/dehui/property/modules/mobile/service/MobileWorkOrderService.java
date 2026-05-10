package com.dehui.property.modules.mobile.service;

import com.dehui.property.common.Result;
import com.dehui.property.modules.mobile.dto.MobileUserProfile;
import com.dehui.property.modules.mobile.dto.MobileWorkOrderHomeResponse;
import com.dehui.property.modules.mobile.dto.MobileWorkOrderRequest;
import com.dehui.property.modules.mobile.dto.MobileWorkOrderResponse;
import com.dehui.property.modules.workorder.entity.WorkOrder;
import com.dehui.property.modules.workorder.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MobileWorkOrderService {

    private final MobileAuthService mobileAuthService;
    private final WorkOrderRepository workOrderRepository;

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
        workOrder.setStatus("CREATED");
        workOrder.setMobileUserId(profile.getId());
        workOrder.setTenantId(profile.getBoundTenantId());
        workOrder.setReporterId(profile.getBoundSysUserId());
        workOrder.setReporterName(resolveReporterName(profile));
        workOrder.setReporterPhone(request.getContactPhone() == null || request.getContactPhone().isBlank()
                ? profile.getPhone()
                : request.getContactPhone());

        return Result.success(toResponse(workOrderRepository.save(workOrder)));
    }

    private String generateOrderNumber() {
        String prefix = "WO-M-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return prefix + String.format("%04d", workOrderRepository.count() + 1);
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
        if ("CREATED".equals(status)) return "已提交";
        if ("ASSIGNED".equals(status)) return "已派单";
        if ("PROCESSING".equals(status)) return "处理中";
        if ("COMPLETED".equals(status)) return "已完成";
        if ("CLOSED".equals(status)) return "已关闭";
        return status == null || status.isBlank() ? "未知" : status;
    }
}
