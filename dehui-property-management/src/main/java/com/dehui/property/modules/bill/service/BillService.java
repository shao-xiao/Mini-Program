package com.dehui.property.modules.bill.service;

import com.dehui.property.common.Result;
import com.dehui.property.modules.bill.dto.BillAuditRequest;
import com.dehui.property.modules.bill.dto.BillCreateRequest;
import com.dehui.property.modules.bill.dto.BillResponse;
import com.dehui.property.modules.bill.entity.Bill;
import com.dehui.property.modules.bill.repository.BillRepository;
import com.dehui.property.modules.contract.entity.Contract;
import com.dehui.property.modules.contract.repository.ContractRepository;
import com.dehui.property.modules.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillService {
    private static final DateTimeFormatter BILL_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final BillRepository billRepository;
    private final ContractRepository contractRepository;
    private final TenantRepository tenantRepository;

    public Result<List<BillResponse>> findAll(Long tenantId, String status, String auditStatus, String billType) {
        List<BillResponse> responses = billRepository.findAll()
                .stream()
                .filter(bill -> tenantId == null || tenantId.equals(bill.getTenantId()))
                .filter(bill -> isBlank(billType) || billType.equals(bill.getBillType()))
                .filter(bill -> matchesAuditStatus(bill, auditStatus))
                .filter(bill -> matchesStatus(bill, status))
                .sorted(Comparator.comparing(
                        Bill::getCreatedTime,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ).reversed())
                .map(this::toResponse)
                .toList();
        return Result.success(responses);
    }

    public Result<List<BillResponse>> findAll() {
        return findAll(null, null, null, null);
    }

    public Result<List<BillResponse>> findByTenantId(Long tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            return Result.error("租户不存在");
        }
        return findAll(tenantId, null, null, null);
    }

    public Result<List<BillResponse>> findByContractId(Long contractId) {
        if (!contractRepository.existsById(contractId)) {
            return Result.error("合同不存在");
        }
        List<BillResponse> responses = billRepository.findByContractIdOrderByPeriodStartDesc(contractId)
                .stream()
                .map(this::toResponse)
                .toList();
        return Result.success(responses);
    }

    public Result<List<BillResponse>> findByStatus(String status) {
        return findAll(null, status, null, null);
    }

    @Transactional
    public Result<BillResponse> create(BillCreateRequest request) {
        String billNumber = normalizeBillNumber(request.getBillNumber());
        if (billRepository.existsByBillNumber(billNumber)) {
            return Result.error("账单编号已存在");
        }

        if (!tenantRepository.existsById(request.getTenantId())) {
            return Result.error("租户不存在");
        }

        Contract contract = null;
        if (request.getContractId() != null) {
            contract = contractRepository.findById(request.getContractId()).orElse(null);
            if (contract == null) {
                return Result.error("合同不存在");
            }
            if (!"ACTIVE".equals(contract.getStatus())) {
                return Result.error("合同未生效，无法创建账单");
            }
            if (!request.getTenantId().equals(contract.getTenantId())) {
                return Result.error("租户与合同不匹配");
            }
            if (billRepository.existsByContractIdAndBillTypeAndPeriodStart(
                    request.getContractId(),
                    request.getBillType(),
                    request.getPeriodStart()
            )) {
                return Result.error("该合同在本账期内已有同类型账单");
            }
        }

        Result<Void> validation = validateBillFields(request.getAmount(), request.getPeriodStart(), request.getPeriodEnd(), request.getDueDate());
        if (validation.getCode() != 200) {
            return Result.error(validation.getMessage());
        }

        Bill bill = new Bill();
        bill.setBillNumber(billNumber);
        bill.setTenantId(request.getTenantId());
        bill.setContractId(request.getContractId());
        bill.setBillType(request.getBillType());
        bill.setTitle(defaultTitle(request.getTitle(), request.getBillType(), request.getPeriodStart()));
        bill.setPeriodStart(request.getPeriodStart());
        bill.setPeriodEnd(request.getPeriodEnd());
        bill.setAmount(request.getAmount());
        bill.setPaidAmount(BigDecimal.ZERO);
        bill.setDueDate(request.getDueDate());
        bill.setStatus("UNPAID");
        bill.setAuditStatus("PENDING");
        bill.setSourceType("MANUAL");
        bill.setRemark(request.getRemark());
        Bill saved = billRepository.save(bill);
        log.info("账单已创建并进入待审核: id={}, number={}, contractId={}", saved.getId(), saved.getBillNumber(), contract == null ? null : contract.getId());

        return Result.success(toResponse(saved));
    }

    @Transactional
    public Result<BillResponse> approve(Long id, String approvedBy, BillAuditRequest request) {
        return billRepository.findById(id)
                .map(bill -> {
                    if ("CANCELLED".equals(bill.getStatus())) {
                        return Result.<BillResponse>error("已取消账单不能发布");
                    }
                    bill.setAuditStatus("APPROVED");
                    bill.setAuditRemark(request == null ? null : request.getAuditRemark());
                    bill.setApprovedBy(isBlank(approvedBy) ? "system" : approvedBy);
                    bill.setApprovedTime(LocalDateTime.now());
                    Bill saved = billRepository.save(bill);
                    log.info("账单审核通过: id={}, number={}, approvedBy={}", saved.getId(), saved.getBillNumber(), saved.getApprovedBy());
                    return Result.success(toResponse(saved));
                })
                .orElseGet(() -> Result.error("账单不存在"));
    }

    @Transactional
    public Result<BillResponse> reject(Long id, String approvedBy, BillAuditRequest request) {
        return billRepository.findById(id)
                .map(bill -> {
                    if ("PAID".equals(bill.getStatus())) {
                        return Result.<BillResponse>error("已收款账单不能驳回");
                    }
                    bill.setAuditStatus("REJECTED");
                    bill.setAuditRemark(request == null ? null : request.getAuditRemark());
                    bill.setApprovedBy(isBlank(approvedBy) ? "system" : approvedBy);
                    bill.setApprovedTime(LocalDateTime.now());
                    Bill saved = billRepository.save(bill);
                    log.info("账单已驳回: id={}, number={}, approvedBy={}", saved.getId(), saved.getBillNumber(), saved.getApprovedBy());
                    return Result.success(toResponse(saved));
                })
                .orElseGet(() -> Result.error("账单不存在"));
    }

    @Transactional
    public Result<BillResponse> pay(Long id) {
        return billRepository.findById(id)
                .map(bill -> {
                    if ("PAID".equals(bill.getStatus())) {
                        return Result.<BillResponse>error("账单已支付");
                    }
                    if ("CANCELLED".equals(bill.getStatus())) {
                        return Result.<BillResponse>error("已取消账单不能收款");
                    }
                    if (!isApproved(bill)) {
                        return Result.<BillResponse>error("账单审核通过后才能确认收款");
                    }
                    bill.setPaidAmount(defaultAmount(bill.getAmount()));
                    bill.setStatus("PAID");
                    Bill saved = billRepository.save(bill);
                    log.info("账单已收款: id={}, number={}", saved.getId(), saved.getBillNumber());
                    return Result.success(toResponse(saved));
                })
                .orElseGet(() -> Result.error("账单不存在"));
    }

    public BillResponse toResponse(Bill bill) {
        BillResponse response = new BillResponse();
        response.setId(bill.getId());
        response.setBillNumber(bill.getBillNumber());
        response.setTenantId(bill.getTenantId());
        if (bill.getTenantId() != null) {
            tenantRepository.findById(bill.getTenantId())
                    .ifPresent(tenant -> response.setTenantName(tenant.getTenantName()));
        }
        response.setContractId(bill.getContractId());
        if (bill.getContractId() != null) {
            contractRepository.findById(bill.getContractId())
                    .ifPresent(contract -> {
                        response.setContractNumber(contract.getContractNumber());
                        response.setContractName(contract.getContractName());
                    });
        }
        response.setBillType(bill.getBillType());
        response.setBillTypeText(toBillTypeText(bill.getBillType()));
        response.setTitle(defaultTitle(bill.getTitle(), bill.getBillType(), bill.getPeriodStart()));
        response.setPeriodStart(bill.getPeriodStart());
        response.setPeriodEnd(bill.getPeriodEnd());
        response.setAmount(defaultAmount(bill.getAmount()));
        response.setPaidAmount(defaultAmount(bill.getPaidAmount()));
        response.setDueDate(bill.getDueDate());
        response.setStatus(bill.getStatus());
        response.setStatusText(toStatusText(bill.getStatus()));
        response.setAuditStatus(effectiveAuditStatus(bill));
        response.setAuditStatusText(toAuditStatusText(response.getAuditStatus()));
        response.setAuditRemark(bill.getAuditRemark());
        response.setApprovedBy(bill.getApprovedBy());
        response.setApprovedTime(bill.getApprovedTime());
        response.setSourceType(bill.getSourceType());
        response.setSourceTypeText(toSourceTypeText(bill.getSourceType()));
        response.setSourceId(bill.getSourceId());
        response.setRemark(bill.getRemark());
        response.setOverdue(isOverdue(bill));
        response.setCreatedTime(bill.getCreatedTime());
        response.setUpdatedTime(bill.getUpdatedTime());
        return response;
    }

    public boolean isApproved(Bill bill) {
        return "APPROVED".equals(effectiveAuditStatus(bill));
    }

    private Result<Void> validateBillFields(BigDecimal amount, LocalDate periodStart, LocalDate periodEnd, LocalDate dueDate) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Result.error("账单金额必须大于0");
        }
        if (periodStart == null) {
            return Result.error("账期开始日期不能为空");
        }
        if (periodEnd == null) {
            return Result.error("账期结束日期不能为空");
        }
        if (periodEnd.isBefore(periodStart)) {
            return Result.error("账期结束日不能早于账期开始日");
        }
        if (dueDate == null) {
            return Result.error("到期日期不能为空");
        }
        if (dueDate.isBefore(periodStart)) {
            return Result.error("到期日不能早于账期开始日");
        }
        return Result.success();
    }

    private boolean matchesStatus(Bill bill, String status) {
        if (isBlank(status)) {
            return true;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if ("OVERDUE".equals(normalized)) {
            return isOverdue(bill);
        }
        return normalized.equals(bill.getStatus());
    }

    private boolean matchesAuditStatus(Bill bill, String auditStatus) {
        if (isBlank(auditStatus)) {
            return true;
        }
        return auditStatus.trim().toUpperCase(Locale.ROOT).equals(effectiveAuditStatus(bill));
    }

    private boolean isOverdue(Bill bill) {
        return !"PAID".equals(bill.getStatus())
                && !"CANCELLED".equals(bill.getStatus())
                && bill.getDueDate() != null
                && bill.getDueDate().isBefore(LocalDate.now());
    }

    private String effectiveAuditStatus(Bill bill) {
        return isBlank(bill.getAuditStatus()) ? "APPROVED" : bill.getAuditStatus();
    }

    private String normalizeBillNumber(String billNumber) {
        if (!isBlank(billNumber)) {
            return billNumber.trim();
        }
        String candidate;
        do {
            candidate = "BILL-" + LocalDate.now().format(BILL_DATE_FORMATTER)
                    + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        } while (billRepository.existsByBillNumber(candidate));
        return candidate;
    }

    private String defaultTitle(String title, String billType, LocalDate periodStart) {
        if (!isBlank(title)) {
            return title.trim();
        }
        String month = periodStart == null ? "" : periodStart.format(DateTimeFormatter.ofPattern("yyyy年MM月")) + " ";
        return month + toBillTypeText(billType) + "账单";
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String toBillTypeText(String billType) {
        if ("RENT".equals(billType)) {
            return "租金";
        }
        if ("PROPERTY".equals(billType)) {
            return "物业费";
        }
        if ("UTILITY".equals(billType)) {
            return "水电煤";
        }
        if ("WATER".equals(billType)) {
            return "水费";
        }
        if ("ELECTRICITY".equals(billType)) {
            return "电费";
        }
        if ("GAS".equals(billType)) {
            return "燃气费";
        }
        if ("PARKING".equals(billType)) {
            return "停车费";
        }
        if ("MEETING".equals(billType) || "MEETING_ROOM".equals(billType)) {
            return "会议室";
        }
        if ("WORK_ORDER".equals(billType) || "REPAIR".equals(billType)) {
            return "维修/工单服务费";
        }
        if ("CLEANING".equals(billType)) {
            return "保洁费";
        }
        if ("DEPOSIT".equals(billType)) {
            return "押金";
        }
        if ("LATE_FEE".equals(billType)) {
            return "滞纳金";
        }
        if ("ADJUSTMENT".equals(billType)) {
            return "调账补差";
        }
        if ("OTHER".equals(billType)) {
            return "其他";
        }
        return isBlank(billType) ? "账单" : billType;
    }

    private String toStatusText(String status) {
        if ("PAID".equals(status)) {
            return "已缴";
        }
        if ("UNPAID".equals(status)) {
            return "待缴";
        }
        if ("OVERDUE".equals(status)) {
            return "已逾期";
        }
        if ("CANCELLED".equals(status)) {
            return "已取消";
        }
        return isBlank(status) ? "未知" : status;
    }

    private String toAuditStatusText(String status) {
        if ("PENDING".equals(status)) {
            return "待审核";
        }
        if ("APPROVED".equals(status)) {
            return "已发布";
        }
        if ("REJECTED".equals(status)) {
            return "已驳回";
        }
        return isBlank(status) ? "已发布" : status;
    }

    private String toSourceTypeText(String sourceType) {
        if ("MANUAL".equals(sourceType)) {
            return "手工账单";
        }
        if ("CONTRACT".equals(sourceType)) {
            return "合同自动出账";
        }
        if ("FEE_RULE".equals(sourceType)) {
            return "周期收费";
        }
        if ("ENERGY".equals(sourceType)) {
            return "能耗抄表";
        }
        if ("PARKING".equals(sourceType)) {
            return "停车账单";
        }
        if ("MEETING_ROOM".equals(sourceType)) {
            return "会议室预约";
        }
        if ("WORK_ORDER".equals(sourceType)) {
            return "工单服务";
        }
        if ("DEV_FIXTURE".equals(sourceType)) {
            return "开发测试";
        }
        return isBlank(sourceType) ? "历史账单" : sourceType;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
