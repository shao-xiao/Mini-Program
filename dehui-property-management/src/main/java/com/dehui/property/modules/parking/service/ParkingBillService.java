package com.dehui.property.modules.parking.service;

import com.dehui.property.modules.bill.entity.Bill;
import com.dehui.property.modules.bill.repository.BillRepository;
import com.dehui.property.modules.parking.entity.ParkingBill;
import com.dehui.property.modules.parking.repository.ParkingBillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ParkingBillService {

    private final ParkingBillRepository parkingBillRepository;
    private final BillRepository billRepository;

    @Transactional
    public ParkingBill create(ParkingBill bill) {
        if (bill.getStatus() == null || bill.getStatus().isBlank()) {
            bill.setStatus("UNPAID");
        }

        if (bill.getBillType() == null || bill.getBillType().isBlank()) {
            bill.setBillType("MONTHLY");
        }
        if (bill.getVip() == null) {
            bill.setVip(false);
        }

        ParkingBill saved = parkingBillRepository.save(bill);
        syncUnifiedBill(saved);
        return parkingBillRepository.save(saved);
    }

    public List<ParkingBill> list() {
        return parkingBillRepository.findAll();
    }

    public List<ParkingBill> listByTenant(Long tenantId) {
        return parkingBillRepository.findByTenantId(tenantId);
    }

    public List<ParkingBill> listBySpace(Long parkingSpaceId) {
        return parkingBillRepository.findByParkingSpaceId(parkingSpaceId);
    }

    @Transactional
    public ParkingBill pay(Long id) {
        ParkingBill bill = parkingBillRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("停车账单不存在"));

        if ("PAID".equals(bill.getStatus())) {
            throw new RuntimeException("停车账单已支付，不能重复支付");
        }

        bill.setStatus("PAID");
        bill.setPaidDate(LocalDate.now());

        ParkingBill saved = parkingBillRepository.save(bill);
        syncUnifiedBill(saved);
        return parkingBillRepository.save(saved);
    }

    @Transactional
    public int syncAllToUnifiedBills() {
        int count = 0;
        for (ParkingBill bill : parkingBillRepository.findAll()) {
            syncUnifiedBill(bill);
            parkingBillRepository.save(bill);
            count++;
        }
        return count;
    }

    public java.util.Map<String, Object> stats() {

        Long total = parkingBillRepository.count();
        Long unpaid = parkingBillRepository.countByStatus("UNPAID");
        Long paid = parkingBillRepository.countByStatus("PAID");

        java.math.BigDecimal totalAmount = parkingBillRepository.findAll()
            .stream()
            .map(b -> b.getAmount() == null ? java.math.BigDecimal.ZERO : b.getAmount())
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        return java.util.Map.of(
            "totalBills", total,
            "paidBills", paid,
            "unpaidBills", unpaid,
            "totalAmount", totalAmount
        );
    }

    private void syncUnifiedBill(ParkingBill parkingBill) {
        if (!Boolean.TRUE.equals(parkingBill.getVip()) && parkingBill.getTenantId() == null) {
            throw new RuntimeException("停车账单未关联租户或VIP，无法同步到账单中心");
        }
        if (parkingBill.getAmount() == null || parkingBill.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("停车账单金额必须大于0");
        }
        if (parkingBill.getBillNumber() == null || parkingBill.getBillNumber().isBlank()) {
            throw new RuntimeException("停车账单编号不能为空");
        }

        Bill bill = null;
        if (parkingBill.getBillId() != null) {
            bill = billRepository.findById(parkingBill.getBillId()).orElse(null);
        }

        String unifiedBillNumber = toUnifiedBillNumber(parkingBill.getBillNumber());
        if (bill == null) {
            bill = billRepository.findByBillNumber(unifiedBillNumber).orElse(null);
        }

        if (bill == null) {
            bill = new Bill();
            bill.setBillNumber(unifiedBillNumber);
        }

        bill.setTenantId(Boolean.TRUE.equals(parkingBill.getVip()) ? null : parkingBill.getTenantId());
        bill.setContractId(null);
        bill.setBillType("PARKING");
        bill.setPeriodStart(parkingBill.getPeriodStart());
        bill.setPeriodEnd(parkingBill.getPeriodEnd());
        bill.setAmount(parkingBill.getAmount());
        bill.setDueDate(parkingBill.getDueDate());
        bill.setStatus(parkingBill.getStatus());
        bill.setPaidAmount("PAID".equals(parkingBill.getStatus())
                ? parkingBill.getAmount()
                : BigDecimal.ZERO);

        Bill savedBill = billRepository.save(bill);
        parkingBill.setBillId(savedBill.getId());
    }

    private String toUnifiedBillNumber(String parkingBillNumber) {
        String trimmed = parkingBillNumber.trim();
        if (trimmed.startsWith("PARK")) {
            return trimmed;
        }
        return "PARK-" + trimmed;
    }
}
