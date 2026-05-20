package com.dehui.property.modules.energy.service;

import com.dehui.property.common.Result;
import com.dehui.property.modules.bill.entity.Bill;
import com.dehui.property.modules.bill.repository.BillRepository;
import com.dehui.property.modules.energy.dto.EnergyAnomalyStatusRequest;
import com.dehui.property.modules.energy.dto.EnergyBillResponse;
import com.dehui.property.modules.energy.dto.EnergyLastReadingResponse;
import com.dehui.property.modules.energy.dto.EnergyMeterResponse;
import com.dehui.property.modules.energy.dto.EnergyReadingRequest;
import com.dehui.property.modules.energy.dto.EnergyReadingResponse;
import com.dehui.property.modules.energy.dto.EnergyStatsResponse;
import com.dehui.property.modules.energy.entity.EnergyMeter;
import com.dehui.property.modules.energy.entity.EnergyRateRule;
import com.dehui.property.modules.energy.entity.EnergyReading;
import com.dehui.property.modules.energy.entity.EnergyRecord;
import com.dehui.property.modules.energy.repository.EnergyMeterRepository;
import com.dehui.property.modules.energy.repository.EnergyRateRuleRepository;
import com.dehui.property.modules.energy.repository.EnergyReadingRepository;
import com.dehui.property.modules.energy.repository.EnergyRecordRepository;
import com.dehui.property.modules.lease.entity.RoomLease;
import com.dehui.property.modules.lease.repository.RoomLeaseRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EnergyService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final EnergyRecordRepository energyRecordRepository;
    private final EnergyRateRuleRepository energyRateRuleRepository;
    private final EnergyMeterRepository energyMeterRepository;
    private final EnergyReadingRepository energyReadingRepository;
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
        rule.setEnergyType(toLegacyEnergyType(rule.getEnergyType()));
        return energyRateRuleRepository.save(rule);
    }

    @Transactional
    public Result<EnergyRateRule> updateRule(Long id, EnergyRateRule request) {
        return energyRateRuleRepository.findById(id)
                .map(rule -> {
                    rule.setEnergyType(toLegacyEnergyType(request.getEnergyType()));
                    rule.setUnitPrice(request.getUnitPrice());
                    rule.setStatus(isBlank(request.getStatus()) ? "ACTIVE" : request.getStatus());
                    rule.setRemark(request.getRemark());
                    return Result.success(energyRateRuleRepository.save(rule));
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

    public Result<List<EnergyMeterResponse>> listMeters(String meterType, Long buildingId, Long floorId, Long roomId, Long tenantId) {
        String normalizedType = normalizeMeterType(meterType);
        List<EnergyMeterResponse> meters = energyMeterRepository.findAll()
                .stream()
                .filter(meter -> isBlank(normalizedType) || normalizedType.equals(meter.getMeterType()))
                .filter(meter -> buildingId == null || buildingId.equals(meter.getBuildingId()))
                .filter(meter -> floorId == null || floorId.equals(meter.getFloorId()))
                .filter(meter -> roomId == null || roomId.equals(meter.getRoomId()))
                .filter(meter -> tenantId == null || tenantId.equals(meter.getTenantId()))
                .sorted(Comparator.comparing(EnergyMeter::getMeterNo, Comparator.nullsLast(String::compareTo)))
                .map(this::toMeterResponse)
                .toList();
        return Result.success(meters);
    }

    public Result<EnergyMeterResponse> findMeter(Long id) {
        return energyMeterRepository.findById(id)
                .map(meter -> Result.success(toMeterResponse(meter)))
                .orElseGet(() -> Result.error("表具不存在"));
    }

    public Result<EnergyLastReadingResponse> lastReading(Long meterId) {
        EnergyMeter meter = energyMeterRepository.findById(meterId).orElse(null);
        if (meter == null) {
            return Result.error("表具不存在");
        }
        Optional<EnergyReading> latest = energyReadingRepository.findTopByMeterIdOrderByPeriodMonthDesc(meterId);
        EnergyLastReadingResponse response = new EnergyLastReadingResponse();
        response.setMeterId(meter.getId());
        response.setMeterNo(meter.getMeterNo());
        response.setMeterType(meter.getMeterType());
        response.setUnit(meter.getUnit());
        response.setMultiplier(defaultMultiplier(meter));
        response.setBuildingId(meter.getBuildingId());
        response.setBuildingName(meter.getBuildingName());
        response.setFloorId(meter.getFloorId());
        response.setFloorName(meter.getFloorName());
        response.setRoomId(meter.getRoomId());
        response.setRoomName(meter.getRoomName());
        response.setTenantId(meter.getTenantId());
        response.setTenantName(meter.getTenantName());
        response.setInstallLocation(meter.getInstallLocation());
        response.setLastPeriodMonth(latest.map(EnergyReading::getPeriodMonth).orElse(null));
        response.setLastReadingDate(latest.map(EnergyReading::getReadingDate).orElse(null));
        response.setPreviousReading(latest.map(EnergyReading::getCurrentReading).orElse(ZERO));
        return Result.success(response);
    }

    public Result<Page<EnergyReadingResponse>> listReadings(String meterType, String meterNo, String periodMonth,
                                                           Long buildingId, Long floorId, Long roomId, Long tenantId,
                                                           String billStatus, Boolean abnormalFlag,
                                                           int page, int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.max(size, 1),
                Sort.by(Sort.Direction.DESC, "createdTime"));
        Page<EnergyReadingResponse> data = energyReadingRepository
                .findAll(readingSpec(meterType, meterNo, periodMonth, buildingId, floorId, roomId, tenantId, billStatus, abnormalFlag), pageRequest)
                .map(this::toReadingResponse);
        return Result.success(data);
    }

    public Result<EnergyReadingResponse> findReading(Long id) {
        return energyReadingRepository.findById(id)
                .map(reading -> Result.success(toReadingResponse(reading)))
                .orElseGet(() -> Result.error("抄表记录不存在"));
    }

    @Transactional
    public Result<EnergyReadingResponse> createReading(EnergyReadingRequest request) {
        EnergyMeter meter = energyMeterRepository.findById(request.getMeterId()).orElse(null);
        if (meter == null) {
            return Result.error("表具不存在");
        }
        if (!"ACTIVE".equals(defaultStatus(meter.getStatus()))) {
            return Result.error("表具已停用或已更换，不能新增抄表");
        }

        String periodMonth = normalizePeriodMonth(request.getPeriodMonth(), request.getReadingDate());
        if (energyReadingRepository.existsByMeterIdAndPeriodMonth(meter.getId(), periodMonth)) {
            return Result.error("该表具本账期已抄表");
        }

        EnergyReading reading = new EnergyReading();
        applyMeterSnapshot(reading, meter);
        applyReadingRequest(reading, request, periodMonth);
        BigDecimal previous = energyReadingRepository
                .findTopByMeterIdAndPeriodMonthLessThanOrderByPeriodMonthDesc(meter.getId(), periodMonth)
                .map(EnergyReading::getCurrentReading)
                .orElse(ZERO);
        Result<Void> calculation = calculate(reading, previous);
        if (calculation.getCode() != 200) {
            return Result.error(calculation.getMessage());
        }
        detectAnomaly(reading);
        reading.setBillStatus("NOT_GENERATED");

        return Result.success(toReadingResponse(energyReadingRepository.save(reading)));
    }

    @Transactional
    public Result<EnergyReadingResponse> updateReading(Long id, EnergyReadingRequest request) {
        EnergyReading reading = energyReadingRepository.findById(id).orElse(null);
        if (reading == null) {
            return Result.error("抄表记录不存在");
        }
        if (!"NOT_GENERATED".equals(defaultBillStatus(reading.getBillStatus()))) {
            return Result.error("已生成或已入账的抄表记录禁止修改");
        }

        EnergyMeter meter = energyMeterRepository.findById(request.getMeterId()).orElse(null);
        if (meter == null) {
            return Result.error("表具不存在");
        }
        String periodMonth = normalizePeriodMonth(request.getPeriodMonth(), request.getReadingDate());
        if (energyReadingRepository.existsByMeterIdAndPeriodMonthAndIdNot(meter.getId(), periodMonth, id)) {
            return Result.error("该表具本账期已抄表");
        }

        applyMeterSnapshot(reading, meter);
        applyReadingRequest(reading, request, periodMonth);
        BigDecimal previous = energyReadingRepository
                .findTopByMeterIdAndPeriodMonthLessThanOrderByPeriodMonthDesc(meter.getId(), periodMonth)
                .map(EnergyReading::getCurrentReading)
                .orElse(ZERO);
        Result<Void> calculation = calculate(reading, previous);
        if (calculation.getCode() != 200) {
            return Result.error(calculation.getMessage());
        }
        detectAnomaly(reading);

        return Result.success(toReadingResponse(energyReadingRepository.save(reading)));
    }

    @Transactional
    public Result<Void> deleteReading(Long id) {
        EnergyReading reading = energyReadingRepository.findById(id).orElse(null);
        if (reading == null) {
            return Result.error("抄表记录不存在");
        }
        if (!"NOT_GENERATED".equals(defaultBillStatus(reading.getBillStatus()))) {
            return Result.error("已生成或已入账的抄表记录禁止删除");
        }
        energyReadingRepository.delete(reading);
        return Result.success();
    }

    @Transactional
    public Result<EnergyReadingResponse> generateReadingBill(Long id) {
        EnergyReading reading = energyReadingRepository.findById(id).orElse(null);
        if (reading == null) {
            return Result.error("抄表记录不存在");
        }
        if (!"NOT_GENERATED".equals(defaultBillStatus(reading.getBillStatus()))) {
            return Result.error("仅未生成账单的抄表记录可以生成账单");
        }
        if (reading.getTenantId() == null) {
            return Result.error("抄表记录未关联租户，无法生成账单");
        }
        if (billRepository.findBySourceTypeAndSourceId("ENERGY", reading.getId()).isPresent()) {
            return Result.error("该抄表记录已生成账单");
        }
        if (money(reading.getSettlementAmount()).compareTo(BigDecimal.ZERO) <= 0) {
            return Result.error("结算金额必须大于0");
        }

        Bill bill = new Bill();
        bill.setBillNumber(uniqueEnergyBillNumber(reading));
        bill.setTenantId(reading.getTenantId());
        bill.setRoomId(reading.getRoomId());
        bill.setBillType(toBillType(reading.getMeterType()));
        YearMonth month = parseMonth(reading.getPeriodMonth());
        bill.setTitle(reading.getPeriodMonth() + " " + toMeterTypeText(reading.getMeterType()) + "账单");
        bill.setPeriodStart(month.atDay(1));
        bill.setPeriodEnd(month.atEndOfMonth());
        bill.setAmount(money(reading.getSettlementAmount()));
        bill.setPaidAmount(BigDecimal.ZERO);
        bill.setDueDate(month.atEndOfMonth().plusDays(7));
        bill.setStatus("UNPAID");
        bill.setAuditStatus("PENDING");
        bill.setSourceType("ENERGY");
        bill.setSourceId(reading.getId());
        bill.setRemark("表号 " + reading.getMeterNo()
                + "，用量 " + money(reading.getUsageAmount())
                + " " + reading.getUnit()
                + "，单价 " + money(reading.getUnitPrice()));

        Bill savedBill = billRepository.save(bill);
        reading.setBillId(savedBill.getId());
        reading.setBillStatus("GENERATED");
        return Result.success(toReadingResponse(energyReadingRepository.save(reading)));
    }

    @Transactional
    public Result<EnergyReadingResponse> markPosted(Long id) {
        EnergyReading reading = energyReadingRepository.findById(id).orElse(null);
        if (reading == null) {
            return Result.error("抄表记录不存在");
        }
        if (!"GENERATED".equals(defaultBillStatus(reading.getBillStatus()))) {
            return Result.error("仅已生成账单的抄表记录可以标记入账");
        }
        if (reading.getBillId() == null) {
            return Result.error("抄表记录未关联账单");
        }

        Bill bill = billRepository.findById(reading.getBillId()).orElse(null);
        if (bill == null) {
            return Result.error("账单不存在");
        }
        bill.setAuditStatus("APPROVED");
        bill.setApprovedBy("energy");
        bill.setApprovedTime(LocalDateTime.now());
        billRepository.save(bill);

        reading.setBillStatus("POSTED");
        return Result.success(toReadingResponse(energyReadingRepository.save(reading)));
    }

    @Transactional
    public Result<EnergyReadingResponse> updateAnomalyStatus(Long id, EnergyAnomalyStatusRequest request) {
        EnergyReading reading = energyReadingRepository.findById(id).orElse(null);
        if (reading == null) {
            return Result.error("抄表记录不存在");
        }
        if (!isValidAnomalyStatus(request.getAbnormalStatus())) {
            return Result.error("无效异常状态");
        }
        reading.setAbnormalStatus(request.getAbnormalStatus());
        return Result.success(toReadingResponse(energyReadingRepository.save(reading)));
    }

    public Result<EnergyStatsResponse> stats(String meterType, String periodMonth, Long buildingId, Long floorId,
                                             Long roomId, Long tenantId, Boolean abnormalFlag, String billStatus) {
        List<EnergyReading> summaryRows = energyReadingRepository.findAll(
                readingSpec(meterType, null, periodMonth, buildingId, floorId, roomId, tenantId, billStatus, abnormalFlag)
        );

        List<String> months = lastSixMonths(periodMonth);
        List<EnergyReading> trendRows = energyReadingRepository.findAll(
                        readingSpec(meterType, null, null, buildingId, floorId, roomId, tenantId, billStatus, abnormalFlag)
                )
                .stream()
                .filter(reading -> months.contains(reading.getPeriodMonth()))
                .toList();

        EnergyStatsResponse response = new EnergyStatsResponse();
        response.setRecordCount(summaryRows.size());
        response.setElectricUsage(sumUsage(summaryRows, "ELECTRIC"));
        response.setWaterUsage(sumUsage(summaryRows, "WATER"));
        response.setGasUsage(sumUsage(summaryRows, "GAS"));
        response.setTotalAmount(sumAmount(summaryRows));
        response.setAverageUsage(summaryRows.isEmpty()
                ? ZERO
                : sum(summaryRows, EnergyReading::getUsageAmount).divide(BigDecimal.valueOf(summaryRows.size()), 2, RoundingMode.HALF_UP));
        response.setAbnormalCount(summaryRows.stream().filter(item -> Boolean.TRUE.equals(item.getAbnormalFlag())).count());
        response.setTrend(buildTrend(months, trendRows));
        response.setTypeStructure(buildTypeStructure(summaryRows));
        response.setRoomRanking(buildRanking(summaryRows, EnergyReading::getRoomId, EnergyReading::getRoomName, true));
        response.setBuildingRanking(buildRanking(summaryRows, EnergyReading::getBuildingId, EnergyReading::getBuildingName, false));
        response.setAnomalies(summaryRows.stream()
                .filter(item -> Boolean.TRUE.equals(item.getAbnormalFlag()))
                .sorted(Comparator.comparing(EnergyReading::getCreatedTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(20)
                .map(this::toAnomalyItem)
                .toList());
        return Result.success(response);
    }

    public Result<List<EnergyReadingResponse>> mobileReadings(Long tenantId, String meterType, String periodMonth) {
        List<EnergyReadingResponse> readings = energyReadingRepository.findAll(
                        readingSpec(meterType, null, periodMonth, null, null, null, tenantId, null, null)
                )
                .stream()
                .sorted(Comparator.comparing(EnergyReading::getPeriodMonth, Comparator.nullsLast(String::compareTo)).reversed())
                .map(this::toReadingResponse)
                .toList();
        return Result.success(readings);
    }

    public Result<List<EnergyBillResponse>> mobileBills(Long tenantId) {
        List<EnergyBillResponse> bills = billRepository.findByTenantIdAndSourceTypeAndAuditStatusOrderByCreatedTimeDesc(tenantId, "ENERGY", "APPROVED")
                .stream()
                .map(this::toEnergyBillResponse)
                .toList();
        return Result.success(bills);
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
                    bill.setRoomId(record.getRoomId());
                    bill.setContractId(null);
                    bill.setBillType(toBillType(record.getEnergyType()));
                    LocalDate periodDate = record.getRecordDate() == null ? LocalDate.now() : record.getRecordDate();
                    bill.setTitle(periodDate.getYear() + "年" + periodDate.getMonthValue() + "月" + toMeterTypeText(record.getEnergyType()) + "账单");
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
                    return Result.success(energyRecordRepository.save(record));
                })
                .orElseGet(() -> Result.error("抄表记录不存在"));
    }

    private Specification<EnergyReading> readingSpec(String meterType, String meterNo, String periodMonth,
                                                     Long buildingId, Long floorId, Long roomId, Long tenantId,
                                                     String billStatus, Boolean abnormalFlag) {
        String normalizedType = normalizeMeterType(meterType);
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!isBlank(normalizedType)) {
                predicates.add(cb.equal(root.get("meterType"), normalizedType));
            }
            if (!isBlank(meterNo)) {
                predicates.add(cb.like(root.get("meterNo"), "%" + meterNo.trim() + "%"));
            }
            if (!isBlank(periodMonth)) {
                predicates.add(cb.equal(root.get("periodMonth"), periodMonth.trim()));
            }
            if (buildingId != null) {
                predicates.add(cb.equal(root.get("buildingId"), buildingId));
            }
            if (floorId != null) {
                predicates.add(cb.equal(root.get("floorId"), floorId));
            }
            if (roomId != null) {
                predicates.add(cb.equal(root.get("roomId"), roomId));
            }
            if (tenantId != null) {
                predicates.add(cb.equal(root.get("tenantId"), tenantId));
            }
            if (!isBlank(billStatus)) {
                predicates.add(cb.equal(root.get("billStatus"), billStatus.trim().toUpperCase(Locale.ROOT)));
            }
            if (abnormalFlag != null) {
                predicates.add(cb.equal(root.get("abnormalFlag"), abnormalFlag));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void applyMeterSnapshot(EnergyReading reading, EnergyMeter meter) {
        reading.setMeterId(meter.getId());
        reading.setMeterNo(meter.getMeterNo());
        reading.setMeterType(normalizeMeterType(meter.getMeterType()));
        reading.setMultiplier(defaultMultiplier(meter));
        reading.setUnit(defaultUnit(meter.getMeterType(), meter.getUnit()));
        reading.setBuildingId(meter.getBuildingId());
        reading.setBuildingName(meter.getBuildingName());
        reading.setFloorId(meter.getFloorId());
        reading.setFloorName(meter.getFloorName());
        reading.setRoomId(meter.getRoomId());
        reading.setRoomName(meter.getRoomName());
        reading.setTenantId(meter.getTenantId());
        reading.setTenantName(meter.getTenantName());
    }

    private void applyReadingRequest(EnergyReading reading, EnergyReadingRequest request, String periodMonth) {
        reading.setReadingDate(request.getReadingDate());
        reading.setPeriodMonth(periodMonth);
        reading.setCurrentReading(money(request.getCurrentReading()));
        reading.setUnitPrice(money(request.getUnitPrice()));
        reading.setOperatorId(request.getOperatorId());
        reading.setOperatorName(request.getOperatorName());
        reading.setRemark(request.getRemark());
    }

    private Result<Void> calculate(EnergyReading reading, BigDecimal previous) {
        BigDecimal current = money(reading.getCurrentReading());
        BigDecimal previousReading = money(previous);
        if (current.compareTo(previousReading) < 0) {
            return Result.error("本次读数不能小于上期读数");
        }
        BigDecimal usage = current.subtract(previousReading)
                .multiply(defaultMultiplier(reading.getMultiplier()))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal amount = usage.multiply(money(reading.getUnitPrice()))
                .setScale(2, RoundingMode.HALF_UP);
        reading.setPreviousReading(previousReading);
        reading.setUsageAmount(usage);
        reading.setSettlementAmount(amount);
        return Result.success();
    }

    private void detectAnomaly(EnergyReading reading) {
        BigDecimal usage = money(reading.getUsageAmount());
        String reason = null;
        if (usage.compareTo(BigDecimal.ZERO) < 0) {
            reason = "NEGATIVE_USAGE";
        } else if (usage.compareTo(BigDecimal.ZERO) == 0) {
            reason = "ZERO_USAGE";
        } else if (hasNoChangeForThreeMonths(reading)) {
            reason = "NO_CHANGE";
        } else {
            BigDecimal average = averageHistoricalUsage(reading);
            if (average.compareTo(BigDecimal.ZERO) > 0
                    && usage.compareTo(average.multiply(new BigDecimal("1.5"))) > 0) {
                reason = "HIGH_USAGE";
            } else if (average.compareTo(BigDecimal.ZERO) > 0
                    && usage.compareTo(average.multiply(new BigDecimal("0.3"))) < 0) {
                reason = "LOW_USAGE";
            }
        }

        reading.setAbnormalFlag(reason != null);
        reading.setAbnormalReason(reason);
        reading.setAbnormalStatus(reason == null ? null : defaultAnomalyStatus(reading.getAbnormalStatus()));
    }

    private boolean hasNoChangeForThreeMonths(EnergyReading reading) {
        List<EnergyReading> history = energyReadingRepository
                .findTop4ByMeterIdAndPeriodMonthLessThanOrderByPeriodMonthDesc(reading.getMeterId(), reading.getPeriodMonth())
                .stream()
                .filter(item -> !Objects.equals(item.getId(), reading.getId()))
                .limit(2)
                .toList();
        if (history.size() < 2) {
            return false;
        }
        BigDecimal current = money(reading.getCurrentReading());
        return history.stream().allMatch(item -> current.compareTo(money(item.getCurrentReading())) == 0);
    }

    private BigDecimal averageHistoricalUsage(EnergyReading reading) {
        List<BigDecimal> values = energyReadingRepository
                .findTop3ByMeterIdAndPeriodMonthLessThanOrderByPeriodMonthDesc(reading.getMeterId(), reading.getPeriodMonth())
                .stream()
                .filter(item -> !Objects.equals(item.getId(), reading.getId()))
                .map(EnergyReading::getUsageAmount)
                .filter(Objects::nonNull)
                .toList();
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private List<EnergyStatsResponse.TrendItem> buildTrend(List<String> months, List<EnergyReading> rows) {
        Map<String, List<EnergyReading>> byMonth = rows.stream()
                .collect(Collectors.groupingBy(EnergyReading::getPeriodMonth));
        return months.stream()
                .map(month -> {
                    List<EnergyReading> monthRows = byMonth.getOrDefault(month, List.of());
                    EnergyStatsResponse.TrendItem item = new EnergyStatsResponse.TrendItem();
                    item.setPeriodMonth(month);
                    item.setElectricUsage(sumUsage(monthRows, "ELECTRIC"));
                    item.setWaterUsage(sumUsage(monthRows, "WATER"));
                    item.setGasUsage(sumUsage(monthRows, "GAS"));
                    item.setSettlementAmount(sumAmount(monthRows));
                    return item;
                })
                .toList();
    }

    private List<EnergyStatsResponse.TypeStructureItem> buildTypeStructure(List<EnergyReading> rows) {
        List<String> types = List.of("ELECTRIC", "WATER", "GAS");
        return types.stream()
                .map(type -> {
                    List<EnergyReading> typeRows = rows.stream()
                            .filter(row -> type.equals(row.getMeterType()))
                            .toList();
                    EnergyStatsResponse.TypeStructureItem item = new EnergyStatsResponse.TypeStructureItem();
                    item.setMeterType(type);
                    item.setUsageAmount(sumUsage(typeRows, type));
                    item.setSettlementAmount(sumAmount(typeRows));
                    return item;
                })
                .toList();
    }

    private List<EnergyStatsResponse.RankingItem> buildRanking(List<EnergyReading> rows,
                                                               Function<EnergyReading, Long> idGetter,
                                                               Function<EnergyReading, String> nameGetter,
                                                               boolean roomRanking) {
        Map<Long, List<EnergyReading>> grouped = rows.stream()
                .filter(row -> idGetter.apply(row) != null)
                .collect(Collectors.groupingBy(idGetter, LinkedHashMap::new, Collectors.toList()));

        return grouped.entrySet()
                .stream()
                .map(entry -> {
                    EnergyReading first = entry.getValue().get(0);
                    EnergyStatsResponse.RankingItem item = new EnergyStatsResponse.RankingItem();
                    item.setId(entry.getKey());
                    String name = defaultName(nameGetter.apply(first), roomRanking ? "未关联房间" : "未关联楼宇");
                    item.setName(name);
                    if (roomRanking) {
                        item.setRoomName(name);
                    } else {
                        item.setBuildingName(name);
                    }
                    item.setUsageAmount(sum(entry.getValue(), EnergyReading::getUsageAmount));
                    item.setSettlementAmount(sumAmount(entry.getValue()));
                    return item;
                })
                .sorted((a, b) -> money(b.getUsageAmount()).compareTo(money(a.getUsageAmount())))
                .limit(10)
                .toList();
    }

    private EnergyStatsResponse.AnomalyItem toAnomalyItem(EnergyReading reading) {
        EnergyStatsResponse.AnomalyItem item = new EnergyStatsResponse.AnomalyItem();
        item.setId(reading.getId());
        item.setMeterNo(reading.getMeterNo());
        item.setMeterType(reading.getMeterType());
        item.setPeriodMonth(reading.getPeriodMonth());
        item.setBuildingName(reading.getBuildingName());
        item.setRoomName(reading.getRoomName());
        item.setUsageAmount(reading.getUsageAmount());
        item.setAbnormalReason(reading.getAbnormalReason());
        item.setAbnormalStatus(reading.getAbnormalStatus());
        return item;
    }

    private EnergyMeterResponse toMeterResponse(EnergyMeter meter) {
        EnergyMeterResponse response = new EnergyMeterResponse();
        response.setId(meter.getId());
        response.setMeterNo(meter.getMeterNo());
        response.setMeterType(normalizeMeterType(meter.getMeterType()));
        response.setBuildingId(meter.getBuildingId());
        response.setBuildingName(meter.getBuildingName());
        response.setFloorId(meter.getFloorId());
        response.setFloorName(meter.getFloorName());
        response.setRoomId(meter.getRoomId());
        response.setRoomName(meter.getRoomName());
        response.setTenantId(meter.getTenantId());
        response.setTenantName(meter.getTenantName());
        response.setInstallLocation(meter.getInstallLocation());
        response.setUnit(defaultUnit(meter.getMeterType(), meter.getUnit()));
        response.setMultiplier(defaultMultiplier(meter));
        response.setBillingMode(isBlank(meter.getBillingMode()) ? "BY_USAGE" : meter.getBillingMode());
        response.setStatus(defaultStatus(meter.getStatus()));
        response.setRemark(meter.getRemark());
        response.setCreatedTime(meter.getCreatedTime());
        response.setUpdatedTime(meter.getUpdatedTime());
        return response;
    }

    private EnergyReadingResponse toReadingResponse(EnergyReading reading) {
        EnergyReadingResponse response = new EnergyReadingResponse();
        response.setId(reading.getId());
        response.setMeterId(reading.getMeterId());
        response.setMeterNo(reading.getMeterNo());
        response.setMeterType(reading.getMeterType());
        response.setReadingDate(reading.getReadingDate());
        response.setPeriodMonth(reading.getPeriodMonth());
        response.setPreviousReading(reading.getPreviousReading());
        response.setCurrentReading(reading.getCurrentReading());
        response.setUsageAmount(reading.getUsageAmount());
        response.setMultiplier(reading.getMultiplier());
        response.setUnit(reading.getUnit());
        response.setUnitPrice(reading.getUnitPrice());
        response.setSettlementAmount(reading.getSettlementAmount());
        response.setBuildingId(reading.getBuildingId());
        response.setBuildingName(reading.getBuildingName());
        response.setFloorId(reading.getFloorId());
        response.setFloorName(reading.getFloorName());
        response.setRoomId(reading.getRoomId());
        response.setRoomName(reading.getRoomName());
        response.setTenantId(reading.getTenantId());
        response.setTenantName(reading.getTenantName());
        response.setBillStatus(defaultBillStatus(reading.getBillStatus()));
        response.setBillId(reading.getBillId());
        response.setAbnormalFlag(Boolean.TRUE.equals(reading.getAbnormalFlag()));
        response.setAbnormalReason(reading.getAbnormalReason());
        response.setAbnormalStatus(reading.getAbnormalStatus());
        response.setOperatorId(reading.getOperatorId());
        response.setOperatorName(reading.getOperatorName());
        response.setRemark(reading.getRemark());
        response.setCreatedTime(reading.getCreatedTime());
        response.setUpdatedTime(reading.getUpdatedTime());
        return response;
    }

    private EnergyBillResponse toEnergyBillResponse(Bill bill) {
        EnergyBillResponse response = new EnergyBillResponse();
        response.setId(bill.getId());
        response.setBillNumber(bill.getBillNumber());
        response.setBillType(bill.getBillType());
        response.setTitle(bill.getTitle());
        response.setTenantId(bill.getTenantId());
        response.setRoomId(bill.getRoomId());
        response.setPeriodStart(bill.getPeriodStart());
        response.setPeriodEnd(bill.getPeriodEnd());
        response.setAmount(bill.getAmount());
        response.setPaidAmount(bill.getPaidAmount());
        response.setStatus(bill.getStatus());
        response.setAuditStatus(bill.getAuditStatus());
        response.setSourceId(bill.getSourceId());
        response.setRemark(bill.getRemark());
        response.setCreatedTime(bill.getCreatedTime());
        return response;
    }

    private List<String> lastSixMonths(String periodMonth) {
        YearMonth anchor = isBlank(periodMonth) ? YearMonth.now() : parseMonth(periodMonth);
        List<String> months = new ArrayList<>();
        for (int i = 5; i >= 0; i -= 1) {
            months.add(anchor.minusMonths(i).format(MONTH_FORMATTER));
        }
        return months;
    }

    private BigDecimal sumUsage(List<EnergyReading> rows, String meterType) {
        return sum(rows.stream().filter(row -> meterType.equals(row.getMeterType())).toList(), EnergyReading::getUsageAmount);
    }

    private BigDecimal sumAmount(List<EnergyReading> rows) {
        return sum(rows, EnergyReading::getSettlementAmount);
    }

    private BigDecimal sum(List<EnergyReading> rows, Function<EnergyReading, BigDecimal> getter) {
        return rows.stream()
                .map(getter)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String uniqueEnergyBillNumber(EnergyReading reading) {
        String base = "ENERGY-" + reading.getPeriodMonth().replace("-", "") + "-" + reading.getId();
        if (!billRepository.existsByBillNumber(base)) {
            return base;
        }
        int index = 2;
        while (billRepository.existsByBillNumber(base + "-" + index)) {
            index += 1;
        }
        return base + "-" + index;
    }

    private YearMonth parseMonth(String periodMonth) {
        return YearMonth.parse(periodMonth, MONTH_FORMATTER);
    }

    private String normalizePeriodMonth(String periodMonth, LocalDate readingDate) {
        if (!isBlank(periodMonth)) {
            return periodMonth.trim();
        }
        if (readingDate == null) {
            return YearMonth.now().format(MONTH_FORMATTER);
        }
        return YearMonth.from(readingDate).format(MONTH_FORMATTER);
    }

    private String normalizeMeterType(String meterType) {
        if (isBlank(meterType)) {
            return "";
        }
        String value = meterType.trim().toUpperCase(Locale.ROOT);
        if ("ELECTRICITY".equals(value)) {
            return "ELECTRIC";
        }
        if ("ELECTRIC".equals(value) || "WATER".equals(value) || "GAS".equals(value)) {
            return value;
        }
        return value;
    }

    private String toLegacyEnergyType(String meterType) {
        String normalized = normalizeMeterType(meterType);
        if ("ELECTRIC".equals(normalized)) {
            return "ELECTRICITY";
        }
        return normalized;
    }

    private String toBillType(String meterType) {
        String normalized = normalizeMeterType(meterType);
        if ("ELECTRIC".equals(normalized)) {
            return "ELECTRICITY";
        }
        if ("WATER".equals(normalized) || "GAS".equals(normalized)) {
            return normalized;
        }
        return "OTHER";
    }

    private String toMeterTypeText(String meterType) {
        return switch (normalizeMeterType(meterType)) {
            case "ELECTRIC" -> "电费";
            case "WATER" -> "水费";
            case "GAS" -> "燃气费";
            default -> "能耗";
        };
    }

    private String defaultUnit(String meterType, String unit) {
        if (!isBlank(unit)) {
            return unit;
        }
        return switch (normalizeMeterType(meterType)) {
            case "ELECTRIC" -> "kWh";
            case "WATER", "GAS" -> "m³";
            default -> "";
        };
    }

    private BigDecimal defaultMultiplier(EnergyMeter meter) {
        return defaultMultiplier(meter.getMultiplier());
    }

    private BigDecimal defaultMultiplier(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP);
        }
        return value;
    }

    private String defaultStatus(String status) {
        return isBlank(status) ? "ACTIVE" : status;
    }

    private String defaultBillStatus(String status) {
        return isBlank(status) ? "NOT_GENERATED" : status;
    }

    private String defaultAnomalyStatus(String status) {
        return isBlank(status) ? "PENDING" : status;
    }

    private String defaultName(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private boolean isValidAnomalyStatus(String status) {
        return "PENDING".equals(status)
                || "CONFIRMED".equals(status)
                || "IGNORED".equals(status)
                || "RESOLVED".equals(status);
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
