package com.dehui.property.modules.parking.service;

import com.dehui.property.modules.bill.entity.Bill;
import com.dehui.property.modules.bill.repository.BillRepository;
import com.dehui.property.modules.parking.dto.ParkingBillResponse;
import com.dehui.property.modules.parking.dto.ParkingBillSyncRequest;
import com.dehui.property.modules.parking.dto.ParkingBillSyncResponse;
import com.dehui.property.modules.parking.entity.ParkingAssignment;
import com.dehui.property.modules.parking.entity.ParkingBill;
import com.dehui.property.modules.parking.entity.ParkingSpace;
import com.dehui.property.modules.parking.repository.ParkingAssignmentRepository;
import com.dehui.property.modules.parking.repository.ParkingBillRepository;
import com.dehui.property.modules.parking.repository.ParkingSpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ParkingBillService {
    private static final String BILL_TYPE_MONTHLY = "monthly";
    private static final String STATUS_UNPAID = "UNPAID";
    private static final String STATUS_PAID = "PAID";
    private static final String STATUS_VOID = "VOID";
    private static final String SYNC_UNSYNCED = "unsynced";
    private static final String SYNC_SYNCED = "synced";
    private static final String SYNC_FAILED = "failed";
    private static final String SOURCE_TYPE_PARKING = "parking";

    private final ParkingBillRepository parkingBillRepository;
    private final ParkingAssignmentRepository assignmentRepository;
    private final ParkingSpaceRepository parkingSpaceRepository;
    private final BillRepository billRepository;
    private final ParkingOperationLogService logService;

    public List<ParkingBillResponse> list(String month, String status, String syncStatus, String keyword) {
        Period period = resolvePeriod(month, null, null);
        return parkingBillRepository.findByPeriodStartGreaterThanEqualAndPeriodEndLessThanEqualOrderByCreatedTimeDesc(period.start(), period.end())
                .stream()
                .filter(bill -> isBlank(status) || normalizeUpper(status).equals(bill.getStatus()))
                .filter(bill -> isBlank(syncStatus) || normalizeLower(syncStatus).equals(defaultSyncStatus(bill)))
                .map(this::toResponse)
                .filter(response -> matchesKeyword(response, keyword))
                .toList();
    }

    public List<ParkingBillResponse> listAll() {
        return parkingBillRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(ParkingBill::getCreatedTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(this::toResponse)
                .toList();
    }

    public List<ParkingBillResponse> listByTenant(Long tenantId) {
        return parkingBillRepository.findByTenantId(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<ParkingBillResponse> listBySpace(Long parkingSpaceId) {
        return parkingBillRepository.findByParkingSpaceId(parkingSpaceId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ParkingBillSyncResponse sync(ParkingBillSyncRequest request) {
        Period period = resolvePeriod(
                request == null ? null : request.getMonth(),
                request == null ? null : request.getPeriodStart(),
                request == null ? null : request.getPeriodEnd()
        );
        ParkingBillSyncResponse response = new ParkingBillSyncResponse();
        generateMissingBills(period, response);
        syncBills(period, response);
        return response;
    }

    @Transactional
    public ParkingBillResponse pay(Long id) {
        ParkingBill bill = parkingBillRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("停车账单不存在"));
        if (STATUS_PAID.equals(bill.getStatus())) {
            throw new RuntimeException("停车账单已支付，不能重复收款");
        }
        if (STATUS_VOID.equals(bill.getStatus())) {
            throw new RuntimeException("已作废停车账单不能收款");
        }

        ParkingBillResponse before = toResponse(bill);
        bill.setStatus(STATUS_PAID);
        bill.setPaidDate(LocalDate.now());
        bill.setPaidAt(LocalDateTime.now());
        ParkingBill saved = parkingBillRepository.save(bill);
        syncFinanceBillPayment(saved);
        ParkingBillResponse after = toResponse(parkingBillRepository.save(saved));
        logService.write("bill", saved.getId(), "PAY_BILL", before, after, null);
        return after;
    }

    @Transactional
    public ParkingBillResponse voidBill(Long id) {
        ParkingBill bill = parkingBillRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("停车账单不存在"));
        if (STATUS_PAID.equals(bill.getStatus())) {
            throw new RuntimeException("已支付停车账单不能作废");
        }
        if (STATUS_VOID.equals(bill.getStatus())) {
            return toResponse(bill);
        }

        ParkingBillResponse before = toResponse(bill);
        Bill financeBill = resolveFinanceBill(bill);
        if (financeBill != null && STATUS_PAID.equals(financeBill.getStatus())) {
            throw new RuntimeException("账单中心已收款，不能作废停车账单");
        }
        bill.setStatus(STATUS_VOID);
        if (financeBill != null) {
            financeBill.setStatus("CANCELLED");
            billRepository.save(financeBill);
        }
        ParkingBill saved = parkingBillRepository.save(bill);
        ParkingBillResponse after = toResponse(saved);
        logService.write("bill", saved.getId(), "VOID_BILL", before, after, null);
        return after;
    }

    public Map<String, Object> stats() {
        List<ParkingBill> bills = parkingBillRepository.findAll();
        long unpaid = bills.stream().filter(item -> STATUS_UNPAID.equals(item.getStatus())).count();
        long paid = bills.stream().filter(item -> STATUS_PAID.equals(item.getStatus())).count();
        BigDecimal totalAmount = bills.stream()
                .map(item -> item.getAmount() == null ? BigDecimal.ZERO : item.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Map.of(
                "totalBills", bills.size(),
                "paidBills", paid,
                "unpaidBills", unpaid,
                "totalAmount", totalAmount
        );
    }

    private void generateMissingBills(Period period, ParkingBillSyncResponse response) {
        List<ParkingAssignment> assignments = assignmentRepository.findAll()
                .stream()
                .filter(assignment -> isEffectiveInPeriod(assignment, period))
                .filter(assignment -> BILL_TYPE_MONTHLY.equals(assignment.getBillingType()))
                .filter(assignment -> assignment.getMonthlyFee() != null && assignment.getMonthlyFee().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        for (ParkingAssignment assignment : assignments) {
            if (parkingBillRepository.existsByAssignmentIdAndPeriodStartAndPeriodEndAndBillType(
                    assignment.getId(), period.start(), period.end(), BILL_TYPE_MONTHLY)) {
                response.skipped("已存在停车账单: assignmentId=" + assignment.getId());
                continue;
            }

            ParkingSpace space = parkingSpaceRepository.findById(assignment.getSpaceId())
                    .orElseThrow(() -> new RuntimeException("车位不存在: " + assignment.getSpaceId()));
            ParkingBill bill = new ParkingBill();
            bill.setBillNo(generateBillNo(period, assignment));
            bill.setBillNumber(bill.getBillNo());
            bill.setAssignmentId(assignment.getId());
            bill.setSpaceId(space.getId());
            bill.setParkingSpaceId(space.getId());
            bill.setSpaceNoSnapshot(space.getSpaceCode());
            bill.setPartyType(assignment.getPartyType());
            bill.setPartyId(assignment.getPartyId());
            bill.setTenantId("tenant".equals(assignment.getPartyType()) ? assignment.getPartyId() : null);
            bill.setVip("vip".equals(assignment.getPartyType()));
            bill.setPartyNameSnapshot(assignment.getPartyNameSnapshot());
            bill.setPlateNoSnapshot(assignment.getPlateNo());
            bill.setPlateNumber(assignment.getPlateNo());
            bill.setBillType(BILL_TYPE_MONTHLY);
            bill.setPeriodStart(period.start());
            bill.setPeriodEnd(period.end());
            bill.setDueDate(period.end().plusDays(7));
            bill.setAmount(assignment.getMonthlyFee());
            bill.setStatus(STATUS_UNPAID);
            bill.setSyncStatus(SYNC_UNSYNCED);
            bill.setRemark("停车月租账单");

            ParkingBill saved = parkingBillRepository.save(bill);
            logService.write("bill", saved.getId(), "GENERATE_BILL", null, toResponse(saved), null);
            response.generated("生成停车账单: " + saved.getBillNo());
        }
    }

    private void syncBills(Period period, ParkingBillSyncResponse response) {
        List<ParkingBill> bills = parkingBillRepository
                .findByPeriodStartGreaterThanEqualAndPeriodEndLessThanEqualOrderByCreatedTimeDesc(period.start(), period.end())
                .stream()
                .filter(bill -> SYNC_UNSYNCED.equals(defaultSyncStatus(bill)) || SYNC_FAILED.equals(defaultSyncStatus(bill)))
                .filter(bill -> !STATUS_VOID.equals(bill.getStatus()))
                .toList();

        for (ParkingBill bill : bills) {
            ParkingBillResponse before = toResponse(bill);
            try {
                Bill financeBill = syncFinanceBill(bill);
                bill.setFinanceBillId(financeBill.getId());
                bill.setBillId(financeBill.getId());
                bill.setSyncStatus(SYNC_SYNCED);
                bill.setSyncError(null);
                ParkingBill saved = parkingBillRepository.save(bill);
                logService.write("bill", saved.getId(), "SYNC_BILL", before, toResponse(saved), null);
                response.synced("同步账单中心: " + saved.getBillNo());
            } catch (Exception e) {
                bill.setSyncStatus(SYNC_FAILED);
                bill.setSyncError(e.getMessage());
                parkingBillRepository.save(bill);
                response.failed("同步失败: " + defaultBillNo(bill) + " - " + e.getMessage());
            }
        }
    }

    private Bill syncFinanceBill(ParkingBill parkingBill) {
        Bill bill = resolveFinanceBill(parkingBill);
        if (bill == null) {
            bill = new Bill();
            bill.setBillNumber(toFinanceBillNumber(parkingBill));
            bill.setAuditStatus("PENDING");
            bill.setInvoiceStatus("UNINVOICED");
        }

        bill.setTenantId(parkingBill.getTenantId());
        bill.setPayerName(parkingBill.getPartyNameSnapshot());
        bill.setContractId(null);
        bill.setBillType("PARKING");
        bill.setTitle("停车费账单 - " + defaultBillNo(parkingBill));
        bill.setPeriodStart(parkingBill.getPeriodStart());
        bill.setPeriodEnd(parkingBill.getPeriodEnd());
        bill.setAmount(parkingBill.getAmount());
        bill.setDueDate(parkingBill.getDueDate());
        bill.setStatus(parkingBill.getStatus());
        bill.setSourceType(SOURCE_TYPE_PARKING);
        bill.setSourceId(parkingBill.getId());
        bill.setRemark("停车模块生成，审核通过后租户可见；付款方：" + parkingBill.getPartyNameSnapshot());
        bill.setPaidAmount(STATUS_PAID.equals(parkingBill.getStatus()) ? parkingBill.getAmount() : BigDecimal.ZERO);
        bill.setPaidTime(STATUS_PAID.equals(parkingBill.getStatus()) ? defaultPaidAt(parkingBill) : null);
        return billRepository.save(bill);
    }

    private void syncFinanceBillPayment(ParkingBill parkingBill) {
        Bill bill = resolveFinanceBill(parkingBill);
        if (bill == null) {
            return;
        }
        bill.setStatus(STATUS_PAID);
        bill.setPaidAmount(parkingBill.getAmount());
        bill.setPaidTime(defaultPaidAt(parkingBill));
        billRepository.save(bill);
    }

    private Bill resolveFinanceBill(ParkingBill parkingBill) {
        if (parkingBill.getFinanceBillId() != null) {
            Bill bill = billRepository.findById(parkingBill.getFinanceBillId()).orElse(null);
            if (bill != null) {
                return bill;
            }
        }
        if (parkingBill.getBillId() != null) {
            Bill bill = billRepository.findById(parkingBill.getBillId()).orElse(null);
            if (bill != null) {
                return bill;
            }
        }
        return billRepository.findBySourceTypeAndSourceId(SOURCE_TYPE_PARKING, parkingBill.getId())
                .orElseGet(() -> billRepository.findBySourceTypeAndSourceId("PARKING", parkingBill.getId()).orElse(null));
    }

    private boolean isEffectiveInPeriod(ParkingAssignment assignment, Period period) {
        boolean activeLike = "active".equals(assignment.getStatus()) || "ended".equals(assignment.getStatus());
        if (!activeLike) {
            return false;
        }
        LocalDate start = assignment.getStartDate() == null ? period.start() : assignment.getStartDate();
        LocalDate end = assignment.getEndDate() == null ? period.end() : assignment.getEndDate();
        return !start.isAfter(period.end()) && !end.isBefore(period.start());
    }

    public ParkingBillResponse toResponse(ParkingBill bill) {
        ParkingBillResponse response = new ParkingBillResponse();
        response.setId(bill.getId());
        response.setBillNo(defaultBillNo(bill));
        response.setBillNumber(defaultBillNo(bill));
        response.setAssignmentId(bill.getAssignmentId());
        Long spaceId = bill.getSpaceId() == null ? bill.getParkingSpaceId() : bill.getSpaceId();
        response.setSpaceId(spaceId);
        response.setParkingSpaceId(spaceId);
        response.setSpaceNoSnapshot(bill.getSpaceNoSnapshot());
        if (isBlank(response.getSpaceNoSnapshot()) && spaceId != null) {
            parkingSpaceRepository.findById(spaceId).ifPresent(space -> response.setSpaceNoSnapshot(space.getSpaceCode()));
        }
        response.setTenantId(bill.getTenantId());
        response.setPartyType(bill.getPartyType());
        response.setPartyTypeText(partyTypeText(bill.getPartyType()));
        response.setPartyId(bill.getPartyId());
        response.setPartyNameSnapshot(bill.getPartyNameSnapshot());
        response.setPlateNoSnapshot(firstNonBlank(bill.getPlateNoSnapshot(), bill.getPlateNumber()));
        response.setPlateNumber(response.getPlateNoSnapshot());
        response.setBillType(bill.getBillType());
        response.setBillTypeText(billTypeText(bill.getBillType()));
        response.setPeriodStart(bill.getPeriodStart());
        response.setPeriodEnd(bill.getPeriodEnd());
        response.setAmount(bill.getAmount());
        response.setStatus(bill.getStatus());
        response.setStatusText(statusText(bill.getStatus()));
        response.setSyncStatus(defaultSyncStatus(bill));
        response.setSyncStatusText(syncStatusText(response.getSyncStatus()));
        response.setFinanceBillId(bill.getFinanceBillId() == null ? bill.getBillId() : bill.getFinanceBillId());
        response.setBillId(response.getFinanceBillId());
        response.setSyncError(bill.getSyncError());
        response.setDueDate(bill.getDueDate());
        response.setPaidDate(bill.getPaidDate());
        response.setPaidAt(bill.getPaidAt());
        response.setRemark(bill.getRemark());
        response.setCreatedTime(bill.getCreatedTime());
        response.setUpdatedTime(bill.getUpdatedTime());
        return response;
    }

    private boolean matchesKeyword(ParkingBillResponse response, String keyword) {
        if (isBlank(keyword)) {
            return true;
        }
        String value = keyword.trim().toLowerCase(Locale.ROOT);
        return contains(response.getBillNo(), value)
                || contains(response.getSpaceNoSnapshot(), value)
                || contains(response.getPartyNameSnapshot(), value)
                || contains(response.getPlateNoSnapshot(), value);
    }

    private Period resolvePeriod(String month, LocalDate periodStart, LocalDate periodEnd) {
        if (periodStart != null && periodEnd != null) {
            if (periodEnd.isBefore(periodStart)) {
                throw new RuntimeException("账期结束日不能早于开始日");
            }
            return new Period(periodStart, periodEnd);
        }
        YearMonth yearMonth = isBlank(month) ? YearMonth.now() : YearMonth.parse(month);
        return new Period(yearMonth.atDay(1), yearMonth.atEndOfMonth());
    }

    private String generateBillNo(Period period, ParkingAssignment assignment) {
        String base = "PARK-" + period.start().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-A" + assignment.getId();
        if (!parkingBillRepository.existsByAssignmentIdAndPeriodStartAndPeriodEndAndBillType(
                assignment.getId(), period.start(), period.end(), BILL_TYPE_MONTHLY)) {
            return base;
        }
        return base + "-" + System.currentTimeMillis();
    }

    private String toFinanceBillNumber(ParkingBill bill) {
        String billNo = defaultBillNo(bill);
        return billNo.startsWith("PARK-") ? billNo : "PARK-" + billNo;
    }

    private LocalDateTime defaultPaidAt(ParkingBill bill) {
        if (bill.getPaidAt() != null) {
            return bill.getPaidAt();
        }
        if (bill.getPaidDate() != null) {
            return bill.getPaidDate().atStartOfDay();
        }
        return LocalDateTime.now();
    }

    private String defaultBillNo(ParkingBill bill) {
        return firstNonBlank(bill.getBillNo(), bill.getBillNumber());
    }

    private String defaultSyncStatus(ParkingBill bill) {
        if (!isBlank(bill.getSyncStatus())) {
            return bill.getSyncStatus();
        }
        return bill.getFinanceBillId() != null || bill.getBillId() != null ? SYNC_SYNCED : SYNC_UNSYNCED;
    }

    private String partyTypeText(String type) {
        return switch (type == null ? "" : type) {
            case "tenant" -> "租户";
            case "vip" -> "VIP";
            case "external" -> "外部客户";
            case "internal" -> "内部员工";
            case "other" -> "其他";
            default -> isBlank(type) ? "-" : type;
        };
    }

    private String billTypeText(String type) {
        return switch (type == null ? "" : type) {
            case "monthly" -> "月租";
            case "temporary", "TEMP" -> "临停";
            case "MONTHLY" -> "月租";
            default -> isBlank(type) ? "-" : type;
        };
    }

    private String statusText(String status) {
        return switch (status == null ? "" : status) {
            case "UNPAID" -> "未支付";
            case "PAID" -> "已支付";
            case "VOID" -> "已作废";
            case "CANCELLED" -> "已取消";
            default -> isBlank(status) ? "-" : status;
        };
    }

    private String syncStatusText(String status) {
        return switch (status == null ? "" : status) {
            case "unsynced" -> "未同步";
            case "synced" -> "已同步";
            case "failed" -> "同步失败";
            default -> isBlank(status) ? "-" : status;
        };
    }

    private boolean contains(String source, String keyword) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String normalizeUpper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeLower(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String first, String second) {
        return !isBlank(first) ? first : second;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record Period(LocalDate start, LocalDate end) {
    }
}
