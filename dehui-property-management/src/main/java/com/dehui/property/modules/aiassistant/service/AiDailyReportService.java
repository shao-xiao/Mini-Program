package com.dehui.property.modules.aiassistant.service;

import com.dehui.property.modules.aiassistant.dto.AiActionItemDTO;
import com.dehui.property.modules.aiassistant.dto.AiDailyReportDTO;
import com.dehui.property.modules.aiassistant.dto.AiRiskItemDTO;
import com.dehui.property.modules.aiassistant.entity.AiDailyReport;
import com.dehui.property.modules.aiassistant.repository.AiDailyReportRepository;
import com.dehui.property.modules.bill.entity.Bill;
import com.dehui.property.modules.bill.repository.BillRepository;
import com.dehui.property.modules.building.repository.RoomRepository;
import com.dehui.property.modules.contract.entity.Contract;
import com.dehui.property.modules.contract.repository.ContractRepository;
import com.dehui.property.modules.equipment.entity.Equipment;
import com.dehui.property.modules.equipment.repository.EquipmentRepository;
import com.dehui.property.modules.inspection.entity.InspectionPlan;
import com.dehui.property.modules.inspection.entity.InspectionRecord;
import com.dehui.property.modules.inspection.repository.InspectionPlanRepository;
import com.dehui.property.modules.inspection.repository.InspectionRepository;
import com.dehui.property.modules.workorder.entity.WorkOrder;
import com.dehui.property.modules.workorder.repository.WorkOrderRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiDailyReportService {
    private static final TypeReference<List<AiRiskItemDTO>> RISK_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<AiActionItemDTO>> ACTION_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> METRICS_TYPE = new TypeReference<>() {
    };

    private final AiDailyReportRepository aiDailyReportRepository;
    private final RoomRepository roomRepository;
    private final ContractRepository contractRepository;
    private final BillRepository billRepository;
    private final WorkOrderRepository workOrderRepository;
    private final EquipmentRepository equipmentRepository;
    private final InspectionRepository inspectionRepository;
    private final InspectionPlanRepository inspectionPlanRepository;
    private final AiAnalysisRuleService ruleService;
    private final ObjectMapper objectMapper;

    @Transactional
    public AiDailyReportDTO getOrGenerate(LocalDate reportDate) {
        LocalDate targetDate = defaultDate(reportDate);
        return aiDailyReportRepository.findByReportDate(targetDate)
                .map(this::toDto)
                .orElseGet(() -> toDto(generateAndSave(targetDate)));
    }

    @Transactional
    public AiDailyReportDTO refresh(LocalDate reportDate) {
        return toDto(generateAndSave(defaultDate(reportDate)));
    }

    public List<AiDailyReportDTO> history() {
        return aiDailyReportRepository.findAllByOrderByReportDateDesc()
                .stream()
                .map(this::toDto)
                .toList();
    }

    public AiDailyReportDTO detail(Long id) {
        return aiDailyReportRepository.findById(id)
                .map(this::toDto)
                .orElse(null);
    }

    private AiDailyReport generateAndSave(LocalDate reportDate) {
        AiDailyReport report = aiDailyReportRepository.findByReportDate(reportDate).orElseGet(AiDailyReport::new);
        report.setReportDate(reportDate);
        report.setGeneratedAt(LocalDateTime.now());

        fillRoomAndContractMetrics(report, reportDate);
        fillBillMetrics(report, reportDate);
        fillWorkOrderMetrics(report, reportDate);
        fillEquipmentMetrics(report);
        fillInspectionMetrics(report, reportDate);

        long expiringContractCount = countExpiringContracts(reportDate);
        Map<String, Object> metrics = buildMetrics(report, expiringContractCount);
        List<AiRiskItemDTO> riskItems = ruleService.buildRiskItems(report, expiringContractCount);
        List<AiActionItemDTO> actionItems = ruleService.buildActionItems(report);

        report.setRiskLevel(ruleService.resolveRiskLevel(riskItems));
        report.setSummaryText(ruleService.buildSummary(report));
        report.setMetricsJson(writeJson(metrics));
        report.setRiskItemsJson(writeJson(riskItems));
        report.setActionItemsJson(writeJson(actionItems));

        return aiDailyReportRepository.save(report);
    }

    private void fillRoomAndContractMetrics(AiDailyReport report, LocalDate reportDate) {
        long roomTotal = count(roomRepository.countRentableRooms());
        long roomRented = Math.min(count(contractRepository.countActiveLeasedRoomIds(reportDate)), roomTotal);
        long activeContracts = contractRepository.findByStatus("ACTIVE")
                .stream()
                .filter(contract -> isContractActiveOn(contract, reportDate))
                .count();

        report.setRoomTotal(roomTotal);
        report.setRoomRented(roomRented);
        report.setRoomAvailable(Math.max(roomTotal - roomRented, 0));
        report.setOccupancyRate(percent(roomRented, roomTotal));
        report.setActiveContractCount(activeContracts);
    }

    private void fillBillMetrics(AiDailyReport report, LocalDate reportDate) {
        List<Bill> bills = billRepository.findAll();
        LocalDateTime dayStart = reportDate.atStartOfDay();
        LocalDateTime dayEnd = reportDate.atTime(LocalTime.MAX);
        YearMonth reportMonth = YearMonth.from(reportDate);

        report.setPaidBillCount(bills.stream().filter(this::isPaidBill).count());
        report.setUnpaidBillCount(bills.stream().filter(this::isUnpaidBill).count());
        report.setOverdueBillCount(bills.stream()
                .filter(this::isUnpaidBill)
                .filter(bill -> bill.getDueDate() != null && bill.getDueDate().isBefore(reportDate))
                .count());
        report.setTodayIncomeAmount(sumPaidAmount(bills, dayStart, dayEnd));
        report.setMonthIncomeAmount(bills.stream()
                .filter(this::isPaidBill)
                .filter(bill -> {
                    LocalDateTime time = paidTime(bill);
                    return time != null && YearMonth.from(time.toLocalDate()).equals(reportMonth);
                })
                .map(this::paidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private void fillWorkOrderMetrics(AiDailyReport report, LocalDate reportDate) {
        List<WorkOrder> workOrders = workOrderRepository.findAll()
                .stream()
                .filter(item -> item.getDeletedAt() == null)
                .toList();

        report.setTodayWorkOrderCount(workOrders.stream()
                .filter(item -> sameDate(firstNonNull(item.getSubmittedTime(), item.getCreatedTime()), reportDate))
                .count());
        report.setProcessingWorkOrderCount(workOrders.stream()
                .filter(item -> isProcessingWorkOrder(item.getStatus()))
                .count());
        report.setHighPriorityWorkOrderCount(workOrders.stream()
                .filter(item -> isHighPriority(item.getPriority()))
                .count());
        report.setOverdueWorkOrderCount(workOrders.stream()
                .filter(this::isOverdueWorkOrder)
                .count());
    }

    private void fillEquipmentMetrics(AiDailyReport report) {
        List<Equipment> equipment = equipmentRepository.findAll();
        report.setDeviceTotal((long) equipment.size());
        report.setFaultDeviceCount(equipment.stream()
                .filter(item -> isFaultStatus(item.getStatus()))
                .count());
    }

    private void fillInspectionMetrics(AiDailyReport report, LocalDate reportDate) {
        List<InspectionRecord> records = inspectionRepository.findByDeletedAtIsNullOrderByInspectionDateDescCreatedTimeDesc();
        List<InspectionPlan> plans = inspectionPlanRepository.findByDeletedAtIsNullOrderByPlannedDateDescCreatedTimeDesc();

        long abnormalCount = records.stream()
                .filter(item -> isAbnormalInspection(item.getResult(), item.getStatus()))
                .count();
        long todayRecordCount = records.stream()
                .filter(item -> reportDate.equals(item.getInspectionDate()))
                .count();
        long todayCompletedCount = records.stream()
                .filter(item -> reportDate.equals(item.getInspectionDate()))
                .filter(item -> "COMPLETED".equals(item.getStatus()) || item.getClosedTime() != null || item.getResult() != null)
                .count();
        long todayPlanCount = plans.stream()
                .filter(item -> reportDate.equals(item.getPlannedDate()))
                .count();

        report.setAbnormalInspectionCount(abnormalCount);
        if (todayPlanCount > 0) {
            report.setTodayInspectionCompletionRate(percent(todayCompletedCount, todayPlanCount));
        } else {
            report.setTodayInspectionCompletionRate(todayRecordCount > 0 ? new BigDecimal("100.00") : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }
    }

    private long countExpiringContracts(LocalDate reportDate) {
        LocalDate limit = reportDate.plusDays(30);
        return contractRepository.findByStatus("ACTIVE")
                .stream()
                .filter(contract -> contract.getEndDate() != null)
                .filter(contract -> !contract.getEndDate().isBefore(reportDate) && !contract.getEndDate().isAfter(limit))
                .count();
    }

    private Map<String, Object> buildMetrics(AiDailyReport report, long expiringContractCount) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("roomTotal", count(report.getRoomTotal()));
        metrics.put("roomRented", count(report.getRoomRented()));
        metrics.put("roomAvailable", count(report.getRoomAvailable()));
        metrics.put("occupancyRate", decimal(report.getOccupancyRate()));
        metrics.put("activeContractCount", count(report.getActiveContractCount()));
        metrics.put("paidBillCount", count(report.getPaidBillCount()));
        metrics.put("unpaidBillCount", count(report.getUnpaidBillCount()));
        metrics.put("overdueBillCount", count(report.getOverdueBillCount()));
        metrics.put("todayIncomeAmount", money(report.getTodayIncomeAmount()));
        metrics.put("monthIncomeAmount", money(report.getMonthIncomeAmount()));
        metrics.put("todayWorkOrderCount", count(report.getTodayWorkOrderCount()));
        metrics.put("processingWorkOrderCount", count(report.getProcessingWorkOrderCount()));
        metrics.put("highPriorityWorkOrderCount", count(report.getHighPriorityWorkOrderCount()));
        metrics.put("overdueWorkOrderCount", count(report.getOverdueWorkOrderCount()));
        metrics.put("deviceTotal", count(report.getDeviceTotal()));
        metrics.put("faultDeviceCount", count(report.getFaultDeviceCount()));
        metrics.put("abnormalInspectionCount", count(report.getAbnormalInspectionCount()));
        metrics.put("todayInspectionCompletionRate", decimal(report.getTodayInspectionCompletionRate()));
        metrics.put("expiringContractCount", expiringContractCount);
        return metrics;
    }

    private AiDailyReportDTO toDto(AiDailyReport report) {
        AiDailyReportDTO dto = new AiDailyReportDTO();
        dto.setId(report.getId());
        dto.setReportDate(report.getReportDate());
        dto.setGeneratedAt(report.getGeneratedAt());
        dto.setRiskLevel(blank(report.getRiskLevel()) ? "NORMAL" : report.getRiskLevel());
        dto.setSummaryText(blank(report.getSummaryText()) ? "暂无数据" : report.getSummaryText());
        dto.setMetrics(readJson(report.getMetricsJson(), METRICS_TYPE, buildMetrics(report, 0)));
        dto.setRiskItems(readJson(report.getRiskItemsJson(), RISK_LIST_TYPE, List.of()));
        dto.setActionItems(readJson(report.getActionItemsJson(), ACTION_LIST_TYPE, List.of()));
        dto.setCreatedAt(report.getCreatedTime());
        dto.setUpdatedAt(report.getUpdatedTime());
        return dto;
    }

    private BigDecimal sumPaidAmount(List<Bill> bills, LocalDateTime start, LocalDateTime end) {
        return bills.stream()
                .filter(this::isPaidBill)
                .filter(bill -> {
                    LocalDateTime time = paidTime(bill);
                    return time != null && !time.isBefore(start) && !time.isAfter(end);
                })
                .map(this::paidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isContractActiveOn(Contract contract, LocalDate date) {
        return "ACTIVE".equals(contract.getStatus())
                && (contract.getStartDate() == null || !contract.getStartDate().isAfter(date))
                && (contract.getEndDate() == null || !contract.getEndDate().isBefore(date));
    }

    private boolean isPaidBill(Bill bill) {
        return "PAID".equals(bill.getStatus());
    }

    private boolean isUnpaidBill(Bill bill) {
        return "UNPAID".equals(bill.getStatus()) || "PENDING".equals(bill.getStatus());
    }

    private BigDecimal paidAmount(Bill bill) {
        return bill.getPaidAmount() == null ? BigDecimal.ZERO : bill.getPaidAmount();
    }

    private LocalDateTime paidTime(Bill bill) {
        return bill.getPaidTime() == null ? bill.getUpdatedTime() : bill.getPaidTime();
    }

    private boolean isProcessingWorkOrder(String status) {
        String value = safe(status);
        return "PROCESSING".equals(value) || "ASSIGNED".equals(value) || "PENDING_CONFIRM".equals(value) || "IN_PROGRESS".equals(value);
    }

    private boolean isHighPriority(String priority) {
        String value = safe(priority);
        return "HIGH".equals(value) || "URGENT".equals(value);
    }

    private boolean isOverdueWorkOrder(WorkOrder item) {
        String status = safe(item.getStatus());
        if ("COMPLETED".equals(status) || "CLOSED".equals(status) || "CANCELLED".equals(status) || "WITHDRAWN".equals(status)) {
            return false;
        }
        LocalDateTime submitted = firstNonNull(item.getSubmittedTime(), item.getCreatedTime());
        if (submitted == null) {
            return false;
        }
        long minutes = java.time.Duration.between(submitted, LocalDateTime.now()).toMinutes();
        if ("URGENT".equals(item.getPriority()) && ("PENDING_ASSIGN".equals(status) || "CREATED".equals(status))) {
            return minutes >= 30;
        }
        return minutes >= 24 * 60;
    }

    private boolean isFaultStatus(String status) {
        String value = safe(status);
        return "FAULT".equals(value) || "ERROR".equals(value) || "BROKEN".equals(value);
    }

    private boolean isAbnormalInspection(String result, String status) {
        return "ABNORMAL".equals(result) || "ABNORMAL".equals(status);
    }

    private boolean sameDate(LocalDateTime value, LocalDate date) {
        return value != null && date.equals(value.toLocalDate());
    }

    private LocalDate defaultDate(LocalDate date) {
        return date == null ? LocalDate.now() : date;
    }

    private long count(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal decimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        return decimal(value);
    }

    private BigDecimal percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .multiply(new BigDecimal("100"))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private <T> T readJson(String json, TypeReference<T> type, T fallback) {
        if (blank(json)) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            return fallback;
        }
    }

    private LocalDateTime firstNonNull(LocalDateTime first, LocalDateTime second) {
        return first == null ? second : first;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
