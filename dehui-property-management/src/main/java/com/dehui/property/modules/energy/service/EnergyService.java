package com.dehui.property.modules.energy.service;

import com.dehui.property.common.Result;
import com.dehui.property.modules.bill.entity.Bill;
import com.dehui.property.modules.bill.repository.BillRepository;
import com.dehui.property.modules.energy.entity.EnergyRecord;
import com.dehui.property.modules.energy.entity.EnergyRateRule;
import com.dehui.property.modules.energy.repository.EnergyRateRuleRepository;
import com.dehui.property.modules.energy.repository.EnergyRecordRepository;
import com.dehui.property.modules.lease.entity.RoomLease;
import com.dehui.property.modules.lease.repository.RoomLeaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EnergyService {
    private final EnergyRecordRepository energyRecordRepository;
    private final EnergyRateRuleRepository energyRateRuleRepository;
    private final RoomLeaseRepository roomLeaseRepository;
    private final BillRepository billRepository;

    public List<EnergyRecord> findAll() {
        return energyRecordRepository.findAll();
    }

    public EnergyRecord save(EnergyRecord energyRecord) {
        return energyRecordRepository.save(energyRecord);
    }

    public List<EnergyRateRule> findRules() {
        return energyRateRuleRepository.findAll();
    }

    @Transactional
    public EnergyRateRule saveRule(EnergyRateRule rule) {
        if (rule.getStatus() == null || rule.getStatus().isBlank()) {
            rule.setStatus("ACTIVE");
        }
        if (rule.getDefaultRule() == null) {
            rule.setDefaultRule(false);
        }
        return energyRateRuleRepository.save(rule);
    }

    @Transactional
    public Result<EnergyRateRule> updateRule(Long id, EnergyRateRule request) {
        return energyRateRuleRepository.findById(id)
                .map(rule -> {
                    rule.setEnergyType(request.getEnergyType());
                    rule.setUnitPrice(request.getUnitPrice());
                    rule.setStatus(request.getStatus() == null || request.getStatus().isBlank()
                            ? "ACTIVE"
                            : request.getStatus());
                    rule.setRemark(request.getRemark());
                    EnergyRateRule saved = energyRateRuleRepository.save(rule);
                    return Result.success(saved);
                })
                .orElseGet(() -> Result.error("计费规则不存在"));
    }

    @Transactional
    public Result<Void> deleteRule(Long id) {
        return energyRateRuleRepository.findById(id)
                .map(rule -> {
                    if (Boolean.TRUE.equals(rule.getDefaultRule())) {
                        return Result.<Void>error("默认计费规则不可删除");
                    }
                    energyRateRuleRepository.delete(rule);
                    return Result.<Void>success(null);
                })
                .orElseGet(() -> Result.error("计费规则不存在"));
    }

    @Transactional
    public Result<EnergyRecord> generateBill(Long recordId) {
        return energyRecordRepository.findById(recordId)
                .map(record -> {
                    if (record.getBillId() != null) {
                        return Result.<EnergyRecord>error("该抄表记录已生成账单");
                    }
                    if (record.getRoomId() == null) {
                        return Result.<EnergyRecord>error("抄表记录未关联房间，无法确认租户");
                    }
                    if (record.getConsumption() == null || record.getConsumption().compareTo(BigDecimal.ZERO) <= 0) {
                        return Result.<EnergyRecord>error("本期用量必须大于0");
                    }

                    RoomLease lease = roomLeaseRepository.findByRoomIdAndStatus(record.getRoomId(), "ACTIVE")
                            .orElse(null);
                    if (lease == null) {
                        return Result.<EnergyRecord>error("该房间没有有效入驻记录，无法生成账单");
                    }

                    EnergyRateRule rule = energyRateRuleRepository
                            .findFirstByEnergyTypeAndStatusOrderByUpdatedTimeDesc(record.getEnergyType(), "ACTIVE")
                            .orElse(null);
                    if (rule == null || rule.getUnitPrice() == null || rule.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                        return Result.<EnergyRecord>error("请先维护该能耗类型的有效单价规则");
                    }

                    BigDecimal amount = record.getConsumption()
                            .multiply(rule.getUnitPrice())
                            .setScale(2, RoundingMode.HALF_UP);
                    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                        return Result.<EnergyRecord>error("结算金额必须大于0");
                    }

                    Bill bill = new Bill();
                    bill.setBillNumber("ENERGY-" + record.getId() + "-" + record.getEnergyType());
                    bill.setTenantId(lease.getTenantId());
                    bill.setContractId(null);
                    bill.setBillType(toBillType(record.getEnergyType()));
                    LocalDate periodDate = record.getRecordDate() == null ? LocalDate.now() : record.getRecordDate();
                    bill.setTitle(periodDate.getYear() + "年" + periodDate.getMonthValue() + "月" + toEnergyTypeText(record.getEnergyType()) + "账单");
                    bill.setPeriodStart(periodDate);
                    bill.setPeriodEnd(periodDate);
                    bill.setAmount(amount);
                    bill.setPaidAmount(BigDecimal.ZERO);
                    bill.setDueDate(periodDate.plusDays(7));
                    bill.setStatus("UNPAID");
                    bill.setAuditStatus("PENDING");
                    bill.setSourceType("ENERGY");
                    bill.setSourceId(record.getId());
                    bill.setRemark("抄表用量 " + record.getConsumption() + " × 单价 " + rule.getUnitPrice());

                    Bill savedBill = billRepository.save(bill);
                    record.setUnitPrice(rule.getUnitPrice());
                    record.setAmount(amount);
                    record.setBillId(savedBill.getId());
                    EnergyRecord savedRecord = energyRecordRepository.save(record);
                    return Result.success(savedRecord);
                })
                .orElseGet(() -> Result.error("抄表记录不存在"));
    }

    private String toBillType(String energyType) {
        if ("ELECTRICITY".equals(energyType)) {
            return "ELECTRICITY";
        }
        if ("WATER".equals(energyType)) {
            return "WATER";
        }
        if ("GAS".equals(energyType)) {
            return "GAS";
        }
        return "OTHER";
    }

    private String toEnergyTypeText(String energyType) {
        if ("ELECTRICITY".equals(energyType)) {
            return "电费";
        }
        if ("WATER".equals(energyType)) {
            return "水费";
        }
        if ("GAS".equals(energyType)) {
            return "燃气费";
        }
        return "能耗";
    }
}
