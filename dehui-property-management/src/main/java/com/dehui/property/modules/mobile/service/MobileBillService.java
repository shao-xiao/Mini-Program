package com.dehui.property.modules.mobile.service;

import com.dehui.property.common.Result;
import com.dehui.property.modules.bill.entity.Bill;
import com.dehui.property.modules.bill.repository.BillRepository;
import com.dehui.property.modules.mobile.dto.MobileBillListResponse;
import com.dehui.property.modules.mobile.dto.MobileBillResponse;
import com.dehui.property.modules.mobile.dto.MobileBillSummaryResponse;
import com.dehui.property.modules.mobile.dto.MobileUserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MobileBillService {

    private final BillRepository billRepository;
    private final MobileAuthService mobileAuthService;

    public Result<MobileBillListResponse> list(String token, String status) {
        MobileUserProfile profile = mobileAuthService.getProfile(token);
        if (profile == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        if (!"TENANT".equals(profile.getUserType()) || profile.getBoundTenantId() == null) {
            return Result.error(403, "请先绑定租户身份后查看账单");
        }

        List<MobileBillResponse> bills = billRepository.findByTenantIdOrderByCreatedTimeDesc(profile.getBoundTenantId())
                .stream()
                .map(this::toResponse)
                .filter(item -> status == null || status.isBlank() || status.equals(item.getStatus()))
                .toList();

        return Result.success(new MobileBillListResponse(profile, summarize(bills), bills));
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
        response.setPeriodStart(bill.getPeriodStart());
        response.setPeriodEnd(bill.getPeriodEnd());
        response.setAmount(defaultAmount(bill.getAmount()));
        response.setPaidAmount(defaultAmount(bill.getPaidAmount()));
        response.setUnpaidAmount(response.getAmount().subtract(response.getPaidAmount()).max(BigDecimal.ZERO));
        response.setDueDate(bill.getDueDate());
        response.setStatus(bill.getStatus());
        response.setStatusText(toStatusText(bill.getStatus()));
        response.setOverdue(!"PAID".equals(bill.getStatus())
                && bill.getDueDate() != null
                && bill.getDueDate().isBefore(LocalDate.now()));
        response.setCreatedTime(bill.getCreatedTime());
        return response;
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String toBillTypeText(String billType) {
        if ("RENT".equals(billType)) {
            return "租金";
        }
        if ("PROPERTY".equals(billType)) {
            return "物业费";
        }
        if ("UTILITY".equals(billType)) {
            return "水电煤";
        }
        if ("PARKING".equals(billType)) {
            return "停车费";
        }
        if ("MEETING".equals(billType) || "MEETING_ROOM".equals(billType)) {
            return "会议室";
        }
        return billType == null || billType.isBlank() ? "账单" : billType;
    }

    private String toStatusText(String status) {
        if ("PAID".equals(status)) {
            return "已支付";
        }
        if ("UNPAID".equals(status)) {
            return "待支付";
        }
        if ("OVERDUE".equals(status)) {
            return "已逾期";
        }
        if ("CANCELLED".equals(status)) {
            return "已取消";
        }
        return status == null || status.isBlank() ? "未知" : status;
    }

    private interface AmountGetter {
        BigDecimal get(MobileBillResponse response);
    }
}
