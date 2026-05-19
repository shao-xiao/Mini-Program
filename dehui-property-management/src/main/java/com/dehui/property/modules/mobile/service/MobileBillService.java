package com.dehui.property.modules.mobile.service;

import com.dehui.property.common.Result;
import com.dehui.property.modules.bill.entity.Bill;
import com.dehui.property.modules.bill.repository.BillRepository;
import com.dehui.property.modules.bill.service.BillService;
import com.dehui.property.modules.mobile.dto.MobileBillListResponse;
import com.dehui.property.modules.mobile.dto.MobileBillResponse;
import com.dehui.property.modules.mobile.dto.MobileBillSummaryResponse;
import com.dehui.property.modules.mobile.dto.MobileUserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MobileBillService {

    private final BillRepository billRepository;
    private final MobileAuthService mobileAuthService;
    private final BillService billService;

    public Result<MobileBillListResponse> list(String token, String status) {
        MobileUserProfile profile = mobileAuthService.getProfile(token);
        if (profile == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        if (!"TENANT".equals(profile.getUserType()) || profile.getBoundTenantId() == null) {
            return Result.error(403, "请先绑定租户身份后查看账单");
        }

        List<MobileBillResponse> bills = billRepository.findApprovedByTenantIdOrderByCreatedTimeDesc(profile.getBoundTenantId())
                .stream()
                .map(this::toResponse)
                .filter(item -> matchesStatus(item, status))
                .toList();

        return Result.success(new MobileBillListResponse(profile, summarize(bills), bills));
    }

    public Result<BillService.InvoiceFile> loadInvoiceFile(String token, Long billId) {
        MobileUserProfile profile = mobileAuthService.getProfile(token);
        if (profile == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        if (!"TENANT".equals(profile.getUserType()) || profile.getBoundTenantId() == null) {
            return Result.error(403, "请先绑定租户身份后下载发票");
        }

        Bill bill = billRepository.findById(billId).orElse(null);
        if (bill == null) {
            return Result.error("账单不存在");
        }
        if (!profile.getBoundTenantId().equals(bill.getTenantId())) {
            return Result.error(403, "无权下载其他租户账单发票");
        }
        if (bill.getAuditStatus() != null && !"APPROVED".equals(bill.getAuditStatus())) {
            return Result.error(403, "账单未发布，不能下载发票");
        }

        return billService.loadInvoiceFile(billId);
    }

    private boolean matchesStatus(MobileBillResponse item, String status) {
        if (status == null || status.isBlank()) {
            return true;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if ("OVERDUE".equals(normalized)) {
            return Boolean.TRUE.equals(item.getOverdue());
        }
        return normalized.equals(item.getStatus());
    }

    private MobileBillSummaryResponse summarize(List<MobileBillResponse> bills) {
        MobileBillSummaryResponse summary = new MobileBillSummaryResponse();
        summary.setTotalCount(bills.size());
        summary.setUnpaidCount(bills.stream().filter(item -> "UNPAID".equals(item.getStatus())).count());
        summary.setOverdueCount(bills.stream().filter(MobileBillResponse::getOverdue).count());
        summary.setPaidCount(bills.stream().filter(item -> "PAID".equals(item.getStatus())).count());
        summary.setTotalAmount(sum(bills, MobileBillResponse::getAmount));
        summary.setUnpaidAmount(sum(bills, MobileBillResponse::getUnpaidAmount));
        summary.setOverdueAmount(sum(
                bills.stream().filter(MobileBillResponse::getOverdue).toList(),
                MobileBillResponse::getUnpaidAmount
        ));
        summary.setPaidAmount(sum(bills, MobileBillResponse::getPaidAmount));
        return summary;
    }

    private BigDecimal sum(List<MobileBillResponse> bills, AmountGetter getter) {
        return bills.stream()
                .map(getter::get)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private MobileBillResponse toResponse(Bill bill) {
        MobileBillResponse response = new MobileBillResponse();
        response.setId(bill.getId());
        response.setBillNumber(bill.getBillNumber());
        response.setBillType(bill.getBillType());
        response.setBillTypeText(toBillTypeText(bill.getBillType()));
        response.setTitle(defaultTitle(bill));
        response.setPeriodStart(bill.getPeriodStart());
        response.setPeriodEnd(bill.getPeriodEnd());
        response.setAmount(defaultAmount(bill.getAmount()));
        response.setPaidAmount(defaultAmount(bill.getPaidAmount()));
        response.setUnpaidAmount(response.getAmount().subtract(response.getPaidAmount()).max(BigDecimal.ZERO));
        response.setDueDate(bill.getDueDate());
        response.setStatus(bill.getStatus());
        response.setStatusText(toStatusText(bill.getStatus()));
        response.setOverdue(!"PAID".equals(bill.getStatus())
                && !"CANCELLED".equals(bill.getStatus())
                && bill.getDueDate() != null
                && bill.getDueDate().isBefore(LocalDate.now()));
        response.setSourceType(bill.getSourceType());
        response.setSourceTypeText(toSourceTypeText(bill.getSourceType()));
        response.setInvoiceStatus(defaultInvoiceStatus(bill));
        response.setInvoiceFileName(bill.getInvoiceFileName());
        response.setInvoiceDownloadUrl("INVOICED".equals(response.getInvoiceStatus())
                ? "/mobile/bills/" + bill.getId() + "/invoice/download"
                : null);
        response.setRemark(bill.getRemark());
        response.setCreatedTime(bill.getCreatedTime());
        return response;
    }

    private String defaultTitle(Bill bill) {
        if (bill.getTitle() != null && !bill.getTitle().isBlank()) {
            return bill.getTitle();
        }
        String month = bill.getPeriodStart() == null
                ? ""
                : bill.getPeriodStart().format(DateTimeFormatter.ofPattern("yyyy年MM月")) + " ";
        return month + toBillTypeText(bill.getBillType()) + "账单";
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String defaultInvoiceStatus(Bill bill) {
        return bill.getInvoiceStatus() == null || bill.getInvoiceStatus().isBlank()
                ? "UNINVOICED"
                : bill.getInvoiceStatus();
    }

    private String toBillTypeText(String billType) {
        if ("RENT".equals(billType)) {
            return "租金";
        }
        if ("PROPERTY".equals(billType) || "PROPERTY_FEE".equals(billType)) {
            return "物业费";
        }
        if ("UTILITY".equals(billType)) {
            return "水电煤";
        }
        if ("WATER".equals(billType)) {
            return "水费";
        }
        if ("ELECTRICITY".equals(billType)) {
            return "电费";
        }
        if ("GAS".equals(billType)) {
            return "燃气费";
        }
        if ("PARKING".equals(billType)) {
            return "停车费";
        }
        if ("MEETING".equals(billType) || "MEETING_ROOM".equals(billType)) {
            return "会议室";
        }
        if ("WORK_ORDER".equals(billType) || "REPAIR".equals(billType)) {
            return "维修/工单服务费";
        }
        if ("CLEANING".equals(billType)) {
            return "保洁费";
        }
        if ("DEPOSIT".equals(billType)) {
            return "押金";
        }
        if ("LATE_FEE".equals(billType)) {
            return "滞纳金";
        }
        if ("ADJUSTMENT".equals(billType)) {
            return "调账补差";
        }
        if ("OTHER".equals(billType)) {
            return "其他";
        }
        return billType == null || billType.isBlank() ? "账单" : billType;
    }

    private String toStatusText(String status) {
        if ("PAID".equals(status)) {
            return "已缴";
        }
        if ("UNPAID".equals(status)) {
            return "待缴";
        }
        if ("OVERDUE".equals(status)) {
            return "已逾期";
        }
        if ("CANCELLED".equals(status)) {
            return "已取消";
        }
        return status == null || status.isBlank() ? "未知" : status;
    }

    private String toSourceTypeText(String sourceType) {
        if ("MANUAL".equals(sourceType)) {
            return "手工账单";
        }
        if ("CONTRACT".equals(sourceType)) {
            return "合同自动出账";
        }
        if ("FEE_RULE".equals(sourceType)) {
            return "周期收费";
        }
        if ("ENERGY".equals(sourceType)) {
            return "能耗抄表";
        }
        if ("PARKING".equals(sourceType)) {
            return "停车账单";
        }
        if ("MEETING_ROOM".equals(sourceType)) {
            return "会议室预约";
        }
        if ("WORK_ORDER".equals(sourceType)) {
            return "工单服务";
        }
        if ("DEV_FIXTURE".equals(sourceType)) {
            return "开发测试";
        }
        return sourceType == null || sourceType.isBlank() ? "历史账单" : sourceType;
    }

    private interface AmountGetter {
        BigDecimal get(MobileBillResponse response);
    }
}
