package com.dehui.property.modules.parking.service;

import com.dehui.property.modules.parking.entity.ParkingBill;
import com.dehui.property.modules.parking.repository.ParkingBillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ParkingBillService {

    private final ParkingBillRepository parkingBillRepository;

    public ParkingBill create(ParkingBill bill) {
        if (bill.getStatus() == null || bill.getStatus().isBlank()) {
            bill.setStatus("UNPAID");
        }

        if (bill.getBillType() == null || bill.getBillType().isBlank()) {
            bill.setBillType("MONTHLY");
        }

        return parkingBillRepository.save(bill);
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

    public ParkingBill pay(Long id) {
        ParkingBill bill = parkingBillRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("停车账单不存在"));

        if ("PAID".equals(bill.getStatus())) {
            throw new RuntimeException("停车账单已支付，不能重复支付");
        }

        bill.setStatus("PAID");
        bill.setPaidDate(LocalDate.now());

        return parkingBillRepository.save(bill);
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
}