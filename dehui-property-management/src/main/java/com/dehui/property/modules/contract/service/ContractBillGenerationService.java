package com.dehui.property.modules.contract.service;

import com.dehui.property.modules.bill.entity.Bill;
import com.dehui.property.modules.bill.repository.BillRepository;
import com.dehui.property.modules.contract.entity.Contract;
import com.dehui.property.modules.contract.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractBillGenerationService {
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    private final ContractRepository contractRepository;
    private final BillRepository billRepository;

    @Transactional
    public int generateDueBills() {
        return generateDueBills(LocalDate.now());
    }

    @Transactional
    public int generateDueBills(LocalDate today) {
        List<Contract> contracts = contractRepository.findByStatus("ACTIVE");
        int createdCount = 0;

        for (Contract contract : contracts) {
            try {
                createdCount += generateForContract(contract, today);
            } catch (Exception e) {
                log.error("合同自动生成账单异常，contractId={}，contractNumber={}",
                        contract.getId(), contract.getContractNumber(), e);
            }
        }

        return createdCount;
    }

    @Transactional
    public int generateDueBillsForContract(Contract contract, LocalDate today) {
        if (contract == null || !"ACTIVE".equals(contract.getStatus())) {
            return 0;
        }

        return generateForContract(contract, today);
    }

    private int generateForContract(Contract contract, LocalDate today) {
        if (contract.getStartDate() == null || contract.getEndDate() == null) {
            return 0;
        }

        int months = paymentMonths(contract.getPaymentCycle());
        int leadDays = contract.getBillingLeadDays() == null ? 7 : Math.max(contract.getBillingLeadDays(), 0);
        LocalDate periodStart = contract.getStartDate();
        int createdCount = 0;
        int guard = 0;

        while (!periodStart.isAfter(contract.getEndDate()) && guard < 240) {
            LocalDate generateDate = previousWorkday(periodStart.minusDays(leadDays));
            if (today.isBefore(generateDate)) {
                break;
            }

            LocalDate periodEnd = periodStart.plusMonths(months).minusDays(1);
            if (periodEnd.isAfter(contract.getEndDate())) {
                periodEnd = contract.getEndDate();
            }

            int periodMonths = periodMonths(periodStart, periodEnd);
            createdCount += createBillIfNeeded(contract, "RENT", contract.getRentAmount(), periodMonths, periodStart, periodEnd);
            createdCount += createBillIfNeeded(contract, "PROPERTY", contract.getPropertyFeeAmount(), periodMonths, periodStart, periodEnd);

            if ("FULL".equals(contract.getPaymentCycle())) {
                break;
            }

            periodStart = periodStart.plusMonths(months);
            guard++;
        }

        return createdCount;
    }

    private int createBillIfNeeded(Contract contract, String billType, BigDecimal monthlyAmount, int periodMonths,
                                   LocalDate periodStart, LocalDate periodEnd) {
        if (monthlyAmount == null || monthlyAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }

        if (billRepository.existsByContractIdAndBillTypeAndPeriodStart(contract.getId(), billType, periodStart)) {
            return 0;
        }

        Bill bill = new Bill();
        bill.setBillNumber(buildBillNumber(contract, billType, periodStart));
        bill.setTenantId(contract.getTenantId());
        bill.setContractId(contract.getId());
        bill.setBillType(billType);
        bill.setTitle(periodStart.format(DateTimeFormatter.ofPattern("yyyy年MM月")) + toBillTypeText(billType) + "账单");
        bill.setPeriodStart(periodStart);
        bill.setPeriodEnd(periodEnd);
        bill.setAmount(monthlyAmount.multiply(BigDecimal.valueOf(periodMonths)));
        bill.setPaidAmount(BigDecimal.ZERO);
        bill.setDueDate(periodStart);
        bill.setStatus("UNPAID");
        bill.setAuditStatus("PENDING");
        bill.setSourceType("CONTRACT");
        bill.setSourceId(contract.getId());
        bill.setRemark("合同自动出账，审核通过后租户可见");
        billRepository.save(bill);
        return 1;
    }

    private int periodMonths(LocalDate periodStart, LocalDate periodEnd) {
        int months = 0;
        LocalDate cursor = periodStart;
        while (!cursor.isAfter(periodEnd) && months < 240) {
            months++;
            cursor = cursor.plusMonths(1);
        }
        return Math.max(months, 1);
    }

    private String buildBillNumber(Contract contract, String billType, LocalDate periodStart) {
        return String.format("CT%s-%s-%s", contract.getId(), billType, periodStart.format(PERIOD_FORMATTER));
    }

    private String toBillTypeText(String billType) {
        if ("RENT".equals(billType)) {
            return "租金";
        }
        if ("PROPERTY".equals(billType)) {
            return "物业费";
        }
        return "费用";
    }

    private int paymentMonths(String paymentCycle) {
        if ("QUARTERLY".equals(paymentCycle)) {
            return 3;
        }
        if ("SEMI_ANNUAL".equals(paymentCycle)) {
            return 6;
        }
        if ("YEARLY".equals(paymentCycle)) {
            return 12;
        }
        if ("FULL".equals(paymentCycle)) {
            return 1200;
        }
        return 1;
    }

    private LocalDate previousWorkday(LocalDate date) {
        LocalDate result = date;
        while (result.getDayOfWeek() == DayOfWeek.SATURDAY || result.getDayOfWeek() == DayOfWeek.SUNDAY) {
            result = result.minusDays(1);
        }
        return result;
    }
}
