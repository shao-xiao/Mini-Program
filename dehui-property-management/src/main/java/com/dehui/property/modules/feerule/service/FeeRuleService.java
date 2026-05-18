package com.dehui.property.modules.feerule.service;

import com.dehui.property.common.Result;
import com.dehui.property.modules.bill.dto.BillResponse;
import com.dehui.property.modules.bill.entity.Bill;
import com.dehui.property.modules.bill.repository.BillRepository;
import com.dehui.property.modules.contract.entity.Contract;
import com.dehui.property.modules.contract.repository.ContractRepository;
import com.dehui.property.modules.feerule.dto.FeeRuleCreateRequest;
import com.dehui.property.modules.feerule.dto.FeeRuleResponse;
import com.dehui.property.modules.feerule.entity.FeeRule;
import com.dehui.property.modules.feerule.repository.FeeRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeeRuleService {

    private final FeeRuleRepository feeRuleRepository;
    private final BillRepository billRepository;
    private final ContractRepository contractRepository;

    public Result<List<FeeRuleResponse>> findAll() {
        return Result.success(
                feeRuleRepository.findAll()
                        .stream()
                        .map(this::toResponse)
                        .collect(Collectors.toList())
        );
    }

    public Result<FeeRuleResponse> create(FeeRuleCreateRequest request) {
        if ("RENT".equals(request.getFeeType()) || "PROPERTY".equals(request.getFeeType())) {
            return Result.error("租金和物业费请在合同台账中设置，由合同自动出账；收费规则仅用于附加周期费用");
        }

        FeeRule rule = new FeeRule();
        rule.setRuleName(request.getRuleName());
        rule.setTenantId(request.getTenantId());
        rule.setContractId(request.getContractId());
        rule.setFeeType(request.getFeeType());
        rule.setAmount(request.getAmount());
        rule.setCycle(request.getCycle());
        rule.setStartDate(request.getStartDate());
        rule.setEndDate(request.getEndDate());
        rule.setGenerateDay(request.getGenerateDay());
        rule.setRemark(request.getRemark());
        rule.setStatus("ACTIVE");

        return Result.success(toResponse(feeRuleRepository.save(rule)));
    }

    @Transactional
    public Result<BillResponse> generateBill(Long ruleId) {
        FeeRule rule = feeRuleRepository.findById(ruleId).orElse(null);
        if (rule == null) {
            return Result.error("收费规则不存在");
        }

        if (!"ACTIVE".equals(rule.getStatus())) {
            return Result.error("收费规则未启用");
        }

        if (rule.getAmount() == null || rule.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return Result.error("收费金额必须大于0");
        }

        if ("RENT".equals(rule.getFeeType()) || "PROPERTY".equals(rule.getFeeType())) {
            return Result.error("租金和物业费请通过合同台账自动出账");
        }

        Contract contract = contractRepository.findById(rule.getContractId()).orElse(null);
        if (contract == null) {
            return Result.error("合同不存在");
        }

        if (!"ACTIVE".equals(contract.getStatus())) {
            return Result.error("合同未生效，无法生成账单");
        }

        if (!rule.getTenantId().equals(contract.getTenantId())) {
            return Result.error("收费规则租户与合同租户不匹配");
        }

        int stepMonths = cycleMonths(rule.getCycle());

        LocalDate today = LocalDate.now();
        LocalDate cursor = resolveCurrentPeriodStart(rule, today, stepMonths);

        LocalDate endLimit = rule.getEndDate();

        for (int i = 0; i < 36; i++) {
            if (endLimit != null && cursor.isAfter(endLimit)) {
                return Result.error("收费规则已超过有效期，无法生成账单");
            }

            if (!billRepository.existsByContractIdAndBillTypeAndPeriodStart(rule.getContractId(), rule.getFeeType(), cursor)) {
                LocalDate periodStart = cursor;
                LocalDate periodEnd = cursor.plusMonths(stepMonths).minusDays(1);

                if (endLimit != null && periodEnd.isAfter(endLimit)) {
                    periodEnd = endLimit;
                }

                int day = rule.getGenerateDay() == null ? 10 : rule.getGenerateDay();
                YearMonth ym = YearMonth.from(periodStart);
                int dueDay = Math.min(day, ym.lengthOfMonth());
                LocalDate dueDate = periodStart.withDayOfMonth(dueDay);

                String billNumber = buildBillNumber(rule, periodStart);

                Bill bill = new Bill();
                bill.setBillNumber(billNumber);
                bill.setTenantId(rule.getTenantId());
                bill.setContractId(rule.getContractId());
                bill.setBillType(rule.getFeeType());
                bill.setTitle(periodStart.format(DateTimeFormatter.ofPattern("yyyy年MM月")) + feeTypeText(rule.getFeeType()) + "账单");
                bill.setPeriodStart(periodStart);
                bill.setPeriodEnd(periodEnd);
                bill.setAmount(rule.getAmount());
                bill.setPaidAmount(BigDecimal.ZERO);
                bill.setDueDate(dueDate);
                bill.setStatus("UNPAID");
                bill.setAuditStatus("PENDING");
                bill.setSourceType("FEE_RULE");
                bill.setSourceId(rule.getId());
                bill.setRemark(rule.getRemark());

                Bill saved = billRepository.save(bill);
                return Result.success(toBillResponse(saved));
            }

            cursor = cursor.plusMonths(stepMonths);
        }

        return Result.error("未来36个月内没有可生成的账期");
    }

    private String buildBillNumber(FeeRule rule, LocalDate periodStart) {
        String ym = periodStart.format(DateTimeFormatter.ofPattern("yyyyMM"));
        return "AUTO-" + rule.getContractId() + "-" + rule.getFeeType() + "-" + ym;
    }

    private int cycleMonths(String cycle) {
        if ("YEARLY".equals(cycle)) {
            return 12;
        }
        if ("QUARTERLY".equals(cycle)) {
            return 3;
        }
        return 1;
    }

    private LocalDate resolveCurrentPeriodStart(FeeRule rule, LocalDate today, int stepMonths) {
        LocalDate cursor = rule.getStartDate() == null
                ? today.withDayOfMonth(1)
                : rule.getStartDate().withDayOfMonth(1);
        LocalDate currentMonth = today.withDayOfMonth(1);
        while (cursor.plusMonths(stepMonths).minusDays(1).isBefore(currentMonth)) {
            cursor = cursor.plusMonths(stepMonths);
        }
        return cursor;
    }

    private String feeTypeText(String feeType) {
        if ("WATER".equals(feeType)) {
            return "水费";
        }
        if ("ELECTRICITY".equals(feeType)) {
            return "电费";
        }
        if ("GAS".equals(feeType)) {
            return "燃气费";
        }
        if ("MEETING_ROOM".equals(feeType)) {
            return "会议室";
        }
        if ("CLEANING".equals(feeType)) {
            return "保洁费";
        }
        if ("PARKING".equals(feeType)) {
            return "停车费";
        }
        if ("OTHER".equals(feeType)) {
            return "其他";
        }
        return feeType == null || feeType.isBlank() ? "费用" : feeType;
    }

    private FeeRuleResponse toResponse(FeeRule rule) {
        FeeRuleResponse response = new FeeRuleResponse();
        response.setId(rule.getId());
        response.setRuleName(rule.getRuleName());
        response.setTenantId(rule.getTenantId());
        response.setContractId(rule.getContractId());
        response.setFeeType(rule.getFeeType());
        response.setAmount(rule.getAmount());
        response.setCycle(rule.getCycle());
        response.setStartDate(rule.getStartDate());
        response.setEndDate(rule.getEndDate());
        response.setGenerateDay(rule.getGenerateDay());
        response.setStatus(rule.getStatus());
        response.setRemark(rule.getRemark());
        response.setCreatedTime(rule.getCreatedTime());
        response.setUpdatedTime(rule.getUpdatedTime());
        return response;
    }

    private BillResponse toBillResponse(Bill bill) {
        BillResponse response = new BillResponse();
        response.setId(bill.getId());
        response.setBillNumber(bill.getBillNumber());
        response.setTenantId(bill.getTenantId());
        response.setContractId(bill.getContractId());
        response.setBillType(bill.getBillType());
        response.setPeriodStart(bill.getPeriodStart());
        response.setPeriodEnd(bill.getPeriodEnd());
        response.setAmount(bill.getAmount());
        response.setPaidAmount(bill.getPaidAmount());
        response.setDueDate(bill.getDueDate());
        response.setStatus(bill.getStatus());
        response.setCreatedTime(bill.getCreatedTime());
        response.setUpdatedTime(bill.getUpdatedTime());
        return response;
    }
}
