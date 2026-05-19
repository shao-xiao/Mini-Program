package com.dehui.property.modules.contract.service;

import com.dehui.property.modules.bill.entity.Bill;
import com.dehui.property.modules.bill.repository.BillRepository;
import com.dehui.property.modules.contract.dto.BillGenerationResult;
import com.dehui.property.modules.contract.entity.Contract;
import com.dehui.property.modules.contract.entity.ContractEvent;
import com.dehui.property.modules.contract.repository.ContractEventRepository;
import com.dehui.property.modules.contract.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final ContractEventRepository contractEventRepository;

    @Transactional
    public BillGenerationResult generateDueBills() {
        return generateDueBills(LocalDate.now());
    }

    @Transactional
    public BillGenerationResult generateDueBills(LocalDate today) {
        BillGenerationResult result = new BillGenerationResult();
        List<Contract> contracts = contractRepository.findByStatus("ACTIVE");

        for (Contract contract : contracts) {
            try {
                result.merge(generateDueBillsForContract(contract, today));
            } catch (Exception e) {
                log.error("合同自动生成账单异常，contractId={}，contractNumber={}",
                        contract.getId(), contract.getContractNumber(), e);
                result.addSkipped("合同 " + contract.getContractNumber() + " 出账异常：" + e.getMessage());
            }
        }

        return result;
    }

    @Transactional
    public BillGenerationResult generateDueBillsForContract(Contract contract, LocalDate today) {
        BillGenerationResult result = new BillGenerationResult();
        if (contract == null || !"ACTIVE".equals(contract.getStatus())) {
            result.addSkipped("合同不存在或不是履约中状态，跳过");
            return result;
        }
        if (contract.getStartDate() == null || contract.getEndDate() == null) {
            result.addSkipped("合同 " + contract.getContractNumber() + " 缺少起止日期，跳过");
            return result;
        }

        LocalDate billableEnd = resolveBillableEnd(contract);
        int cycleMonths = paymentMonths(contract.getPaymentCycle());
        LocalDate dueLimit = today.plusDays(resolveAdvanceBillDays(contract));
        LocalDate cursor = contract.getStartDate();
        LocalDate generatedUntil = contract.getBillGeneratedUntil();
        int guard = 0;

        createDepositIfNeeded(contract, dueLimit, result);

        while (!cursor.isAfter(billableEnd) && guard < 240) {
            LocalDate periodEnd = "FULL".equals(contract.getPaymentCycle())
                    ? billableEnd
                    : cursor.plusMonths(cycleMonths).minusDays(1);
            if (periodEnd.isAfter(billableEnd)) {
                periodEnd = billableEnd;
            }

            LocalDate dueDate = resolveDueDate(contract, cursor);
            if (dueDate.isAfter(dueLimit)) {
                result.addSkipped("合同 " + contract.getContractNumber() + " " + cursor + " 未到提前出账日期，跳过");
                break;
            }

            createPeriodBillIfNeeded(contract, "RENT", contract.getRentAmount(), cursor, periodEnd, dueDate, result);
            createPeriodBillIfNeeded(contract, "PROPERTY_FEE", contract.getPropertyFeeAmount(), cursor, periodEnd, dueDate, result);
            generatedUntil = generatedUntil == null || periodEnd.isAfter(generatedUntil) ? periodEnd : generatedUntil;

            if ("FULL".equals(contract.getPaymentCycle())) {
                break;
            }
            cursor = cursor.plusMonths(cycleMonths);
            guard++;
        }

        if (generatedUntil != null) {
            contract.setBillGeneratedUntil(generatedUntil);
            contractRepository.save(contract);
        }
        if (result.getGenerated() > 0) {
            writeEvent(contract, "GENERATE_BILL", "生成账单 " + result.getGenerated() + " 张");
        }
        return result;
    }

    private void createDepositIfNeeded(Contract contract, LocalDate dueLimit, BillGenerationResult result) {
        BigDecimal deposit = defaultAmount(contract.getDepositAmount());
        if (deposit.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        LocalDate dueDate = resolveDueDate(contract, contract.getStartDate());
        if (dueDate.isAfter(dueLimit)) {
            result.addSkipped("合同 " + contract.getContractNumber() + " 押金未到提前出账日期，跳过");
            return;
        }
        createBillIfNeeded(contract, "DEPOSIT", deposit, contract.getStartDate(), contract.getStartDate(), dueDate, result);
    }

    private void createPeriodBillIfNeeded(Contract contract, String billType, BigDecimal monthlyAmount,
                                          LocalDate periodStart, LocalDate periodEnd, LocalDate dueDate,
                                          BillGenerationResult result) {
        BigDecimal amount = defaultAmount(monthlyAmount);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal total = amount.multiply(BigDecimal.valueOf(periodMonths(periodStart, periodEnd)));
        createBillIfNeeded(contract, billType, total, periodStart, periodEnd, dueDate, result);
    }

    private void createBillIfNeeded(Contract contract, String billType, BigDecimal amount,
                                    LocalDate periodStart, LocalDate periodEnd, LocalDate dueDate,
                                    BillGenerationResult result) {
        if (billRepository.existsByContractIdAndBillTypeAndPeriodStartAndPeriodEnd(
                contract.getId(), billType, periodStart, periodEnd)) {
            result.addSkipped("合同 " + contract.getContractNumber() + " " + periodStart + " " + billType + " 已存在，跳过");
            return;
        }

        String billNumber = buildBillNumber(contract, billType, periodStart);
        if (billRepository.existsByBillNumber(billNumber)) {
            result.addSkipped("账单编号 " + billNumber + " 已存在，跳过");
            return;
        }

        Bill bill = new Bill();
        bill.setBillNumber(billNumber);
        bill.setTenantId(contract.getTenantId());
        bill.setContractId(contract.getId());
        bill.setRoomId(contract.getRoomId());
        bill.setBillType(billType);
        bill.setTitle(periodStart.format(DateTimeFormatter.ofPattern("yyyy年MM月")) + toBillTypeText(billType) + "账单");
        bill.setPeriodStart(periodStart);
        bill.setPeriodEnd(periodEnd);
        bill.setAmount(amount);
        bill.setPaidAmount(BigDecimal.ZERO);
        bill.setDueDate(dueDate);
        bill.setStatus("UNPAID");
        bill.setAuditStatus("PENDING");
        bill.setSourceType("CONTRACT");
        bill.setSourceId(contract.getId());
        bill.setRemark("合同自动出账，审核通过后租户可见");
        billRepository.save(bill);
        result.addGenerated();
    }

    private LocalDate resolveBillableEnd(Contract contract) {
        LocalDate end = contract.getEndDate();
        if (contract.getTerminationDate() != null && contract.getTerminationDate().isBefore(end)) {
            return contract.getTerminationDate();
        }
        return end;
    }

    private LocalDate resolveDueDate(Contract contract, LocalDate periodStart) {
        int dueDay = contract.getDueDay() == null ? periodStart.getDayOfMonth() : contract.getDueDay();
        int maxDay = periodStart.lengthOfMonth();
        return periodStart.withDayOfMonth(Math.min(Math.max(dueDay, 1), maxDay));
    }

    private int resolveAdvanceBillDays(Contract contract) {
        if (contract.getAdvanceBillDays() != null) {
            return Math.max(contract.getAdvanceBillDays(), 0);
        }
        if (contract.getBillingLeadDays() != null) {
            return Math.max(contract.getBillingLeadDays(), 0);
        }
        return 7;
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
        if ("PROPERTY_FEE".equals(billType) || "PROPERTY".equals(billType)) {
            return "物业费";
        }
        if ("DEPOSIT".equals(billType)) {
            return "押金";
        }
        return "费用";
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void writeEvent(Contract contract, String action, String remark) {
        ContractEvent event = new ContractEvent();
        event.setContractId(contract.getId());
        event.setAction(action);
        event.setBeforeStatus(contract.getStatus());
        event.setAfterStatus(contract.getStatus());
        event.setOperatorName("system");
        event.setRemark(remark);
        contractEventRepository.save(event);
    }
}
