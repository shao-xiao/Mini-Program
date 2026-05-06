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

        int stepMonths = "QUARTERLY".equals(rule.getCycle()) ? 3 : 1;

        LocalDate today = LocalDate.now();
        LocalDate cursor = today.withDayOfMonth(1);

        if (rule.getStartDate() != null && cursor.isBefore(rule.getStartDate().withDayOfMonth(1))) {
            cursor = rule.getStartDate().withDayOfMonth(1);
        }

        LocalDate endLimit = rule.getEndDate();

        for (int i = 0; i < 36; i++) {
            if (endLimit != null && cursor.isAfter(endLimit)) {
                return Result.error("收费规则已超过有效期，无法生成账单");
            }

            if (!billRepository.existsByContractIdAndPeriodStart(rule.getContractId(), cursor)) {
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
                bill.setPeriodStart(periodStart);
                bill.setPeriodEnd(periodEnd);
                bill.setAmount(rule.getAmount());
                bill.setPaidAmount(BigDecimal.ZERO);
                bill.setDueDate(dueDate);
                bill.setStatus("UNPAID");

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
