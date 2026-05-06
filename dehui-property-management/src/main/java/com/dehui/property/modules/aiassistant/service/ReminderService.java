package com.dehui.property.modules.aiassistant.service;

import com.dehui.property.modules.bill.entity.Bill;
import com.dehui.property.modules.bill.repository.BillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReminderService {

    private final BillRepository billRepository;

    public List<Map<String, Object>> getOverdueBills() {
        LocalDate today = LocalDate.now();

        return billRepository.findByStatusAndDueDateBefore("UNPAID", today)
                .stream()
                .map(bill -> Map.<String, Object>of(
                        "billId", bill.getId(),
                        "tenantId", bill.getTenantId(),
                        "amount", bill.getAmount(),
                        "dueDate", bill.getDueDate(),
                        "daysOverdue", ChronoUnit.DAYS.between(bill.getDueDate(), today),
                        "message", "尊敬的租户，您有一笔" + bill.getAmount() + "元账单已逾期，请尽快支付。"
                ))
                .toList();
    }
}