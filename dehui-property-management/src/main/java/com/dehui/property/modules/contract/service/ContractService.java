package com.dehui.property.modules.contract.service;

import com.dehui.property.common.Result;
import com.dehui.property.modules.building.entity.Room;
import com.dehui.property.modules.building.repository.RoomRepository;
import com.dehui.property.modules.contract.dto.ContractActionRequest;
import com.dehui.property.modules.contract.dto.ContractCreateRequest;
import com.dehui.property.modules.contract.dto.ContractEventResponse;
import com.dehui.property.modules.contract.dto.ContractResponse;
import com.dehui.property.modules.contract.entity.Contract;
import com.dehui.property.modules.contract.entity.ContractEvent;
import com.dehui.property.modules.contract.repository.ContractEventRepository;
import com.dehui.property.modules.contract.repository.ContractRepository;
import com.dehui.property.modules.lease.entity.Occupancy;
import com.dehui.property.modules.lease.repository.OccupancyRepository;
import com.dehui.property.modules.tenant.entity.Tenant;
import com.dehui.property.modules.tenant.repository.TenantRepository;
import com.dehui.property.modules.tenant.service.TenantContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContractService {
    private final ContractRepository contractRepository;
    private final TenantRepository tenantRepository;
    private final RoomRepository roomRepository;
    private final OccupancyRepository occupancyRepository;
    private final ContractEventRepository contractEventRepository;
    private final ContractBillGenerationService contractBillGenerationService;
    private final TenantContactService tenantContactService;

    public Result<List<ContractResponse>> findAll() {
        return Result.success(contractRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList());
    }

    public Result<ContractResponse> findById(Long id) {
        return contractRepository.findById(id)
                .map(contract -> Result.success(toResponse(contract)))
                .orElseGet(() -> Result.error("合同不存在"));
    }

    @Transactional
    public Result<ContractResponse> create(ContractCreateRequest request) {
        Result<Void> validation = validateContractRequest(request, null);
        if (validation.getCode() != 200) {
            return Result.error(validation.getMessage());
        }

        Long tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Result.error("请输入租户名称");
        }

        Contract contract = new Contract();
        contract.setContractNumber(request.getContractNumber().trim());
        contract.setContractName(trimToNull(request.getContractName()));
        contract.setTenantId(tenantId);
        contract.setRoomId(request.getRoomId());
        contract.setLeaseId(null);
        contract.setStartDate(request.getStartDate());
        contract.setEndDate(request.getEndDate());
        contract.setRentAmount(defaultAmount(request.getRentAmount()));
        contract.setPropertyFeeAmount(defaultAmount(request.getPropertyFeeAmount()));
        contract.setDepositAmount(defaultAmount(request.getDepositAmount()));
        contract.setPaymentCycle(defaultPaymentCycle(request.getPaymentCycle()));
        contract.setBillingDay(request.getBillingDay() == null ? 1 : request.getBillingDay());
        contract.setDueDay(request.getDueDay() == null ? 10 : request.getDueDay());
        contract.setPaymentTerms(trimToNull(request.getPaymentTerms()));
        int advanceDays = request.getAdvanceBillDays() != null
                ? request.getAdvanceBillDays()
                : (request.getBillingLeadDays() == null ? 7 : request.getBillingLeadDays());
        contract.setAdvanceBillDays(Math.max(advanceDays, 0));
        contract.setBillingLeadDays(contract.getAdvanceBillDays());
        contract.setBillingRule(defaultBillingRule(request.getBillingRule(), contract.getAdvanceBillDays()));
        contract.setStatus("DRAFT");
        contract.setRemark(trimToNull(request.getRemark()));

        Contract saved = contractRepository.save(contract);
        tenantContactService.syncPrimaryContact(tenantId, request.getContactPerson(), request.getContactPhone());
        writeEvent(saved, "CREATE", null, saved.getStatus(), null, null, "创建合同");
        return Result.success(toResponse(saved));
    }

    public Result<List<ContractResponse>> findActivePendingCheckin() {
        List<ContractResponse> responses = contractRepository.findByStatus("ACTIVE")
                .stream()
                .filter(contract -> occupancyRepository.findByContractIdAndStatus(contract.getId(), "ACTIVE").isEmpty())
                .map(this::toResponse)
                .toList();
        return Result.success(responses);
    }

    @Transactional
    public Result<ContractResponse> activate(Long id) {
        return contractRepository.findById(id)
                .map(contract -> {
                    if (!"DRAFT".equals(contract.getStatus()) && !"PENDING".equals(contract.getStatus())) {
                        return Result.<ContractResponse>error("只有草稿或待生效合同可以生效");
                    }
                    Result<Void> overlap = validateNoActiveOverlap(contract.getRoomId(), contract.getStartDate(), contract.getEndDate(), contract.getId());
                    if (overlap.getCode() != 200) {
                        return Result.<ContractResponse>error(overlap.getMessage());
                    }
                    String before = contract.getStatus();
                    contract.setStatus("ACTIVE");
                    Contract saved = contractRepository.save(contract);
                    updateRoomStatus(saved.getRoomId(), "RESERVED");
                    writeEvent(saved, "ACTIVATE", before, saved.getStatus(), null, null, "合同生效");
                    contractBillGenerationService.generateDueBillsForContract(saved, LocalDate.now());
                    return Result.success(toResponse(saved));
                })
                .orElseGet(() -> Result.error("合同不存在"));
    }

    @Transactional
    public Result<ContractResponse> checkIn(Long id, ContractActionRequest request) {
        return contractRepository.findById(id)
                .map(contract -> {
                    if (!"ACTIVE".equals(contract.getStatus())) {
                        return Result.<ContractResponse>error("只有履约中合同可以办理入驻");
                    }
                    if (occupancyRepository.findByContractIdAndStatus(contract.getId(), "ACTIVE").isPresent()) {
                        return Result.<ContractResponse>error("该合同已办理入驻");
                    }
                    Occupancy occupancy = new Occupancy();
                    occupancy.setContractId(contract.getId());
                    occupancy.setTenantId(contract.getTenantId());
                    occupancy.setRoomId(contract.getRoomId());
                    occupancy.setCheckInDate(request != null && request.getActionDate() != null ? request.getActionDate() : contract.getStartDate());
                    occupancy.setPlannedEndDate(contract.getEndDate());
                    occupancy.setStatus("ACTIVE");
                    occupancy.setRemark(request == null ? null : trimToNull(request.getRemark()));
                    occupancyRepository.save(occupancy);
                    updateRoomStatus(contract.getRoomId(), "LEASED");
                    writeEvent(contract, "CHECK_IN", contract.getStatus(), contract.getStatus(),
                            request == null ? null : request.getOperatorId(),
                            request == null ? null : request.getOperatorName(),
                            request == null ? "办理入驻" : defaultText(request.getRemark(), "办理入驻"));
                    return Result.success(toResponse(contract));
                })
                .orElseGet(() -> Result.error("合同不存在"));
    }

    @Transactional
    public Result<ContractResponse> terminate(Long id, ContractActionRequest request) {
        return contractRepository.findById(id)
                .map(contract -> {
                    if ("TERMINATED".equals(contract.getStatus())) {
                        return Result.<ContractResponse>error("合同已终止");
                    }
                    if ("CANCELLED".equals(contract.getStatus())) {
                        return Result.<ContractResponse>error("已作废合同不能终止");
                    }
                    String before = contract.getStatus();
                    LocalDate actionDate = request != null && request.getActionDate() != null ? request.getActionDate() : LocalDate.now();
                    contract.setStatus("TERMINATED");
                    contract.setTerminatedAt(LocalDateTime.now());
                    contract.setTerminationDate(actionDate);
                    contract.setTerminationReason(request == null ? null : trimToNull(request.getReason()));
                    Contract saved = contractRepository.save(contract);
                    closeOccupancy(saved, actionDate);
                    updateRoomStatus(saved.getRoomId(), "VACANT");
                    writeEvent(saved, "TERMINATE", before, saved.getStatus(),
                            request == null ? null : request.getOperatorId(),
                            request == null ? null : request.getOperatorName(),
                            request == null ? "合同终止" : defaultText(request.getReason(), "合同终止"));
                    writeEvent(saved, "CHECK_OUT", before, saved.getStatus(),
                            request == null ? null : request.getOperatorId(),
                            request == null ? null : request.getOperatorName(),
                            "退租完成");
                    return Result.success(toResponse(saved));
                })
                .orElseGet(() -> Result.error("合同不存在"));
    }

    @Transactional
    public Result<ContractResponse> cancel(Long id, ContractActionRequest request) {
        return contractRepository.findById(id)
                .map(contract -> {
                    if (occupancyRepository.findByContractIdAndStatus(contract.getId(), "ACTIVE").isPresent()) {
                        return Result.<ContractResponse>error("已入驻合同不能作废，请办理终止");
                    }
                    if ("CANCELLED".equals(contract.getStatus())) {
                        return Result.<ContractResponse>error("合同已作废");
                    }
                    String before = contract.getStatus();
                    contract.setStatus("CANCELLED");
                    contract.setCancelledAt(LocalDateTime.now());
                    Contract saved = contractRepository.save(contract);
                    updateRoomStatus(saved.getRoomId(), "VACANT");
                    writeEvent(saved, "CANCEL", before, saved.getStatus(),
                            request == null ? null : request.getOperatorId(),
                            request == null ? null : request.getOperatorName(),
                            request == null ? "合同作废" : defaultText(request.getReason(), "合同作废"));
                    return Result.success(toResponse(saved));
                })
                .orElseGet(() -> Result.error("合同不存在"));
    }

    public Result<List<ContractEventResponse>> events(Long contractId) {
        if (!contractRepository.existsById(contractId)) {
            return Result.error("合同不存在");
        }
        return Result.success(contractEventRepository.findByContractIdOrderByCreatedTimeDesc(contractId)
                .stream()
                .map(this::toEventResponse)
                .toList());
    }

    private Result<Void> validateContractRequest(ContractCreateRequest request, Long currentId) {
        if (request.getContractNumber() == null || request.getContractNumber().isBlank()) {
            return Result.error("合同编号不能为空");
        }
        if (contractRepository.existsByContractNumber(request.getContractNumber().trim())) {
            return Result.error("合同编号已存在");
        }
        if (request.getTenantId() == null && (request.getTenantName() == null || request.getTenantName().isBlank())) {
            return Result.error("租户名称不能为空");
        }
        if (request.getTenantId() != null && !tenantRepository.existsById(request.getTenantId())) {
            return Result.error("租户不存在");
        }
        if (request.getRoomId() == null || !roomRepository.existsById(request.getRoomId())) {
            return Result.error("房间必选");
        }
        if (request.getStartDate() == null || request.getEndDate() == null) {
            return Result.error("合同开始和结束日期不能为空");
        }
        if (request.getStartDate().isAfter(request.getEndDate())) {
            return Result.error("开始日期不能晚于结束日期");
        }
        if (isNegative(request.getRentAmount()) || isNegative(request.getPropertyFeeAmount()) || isNegative(request.getDepositAmount())) {
            return Result.error("月租、物业费、押金不能小于0");
        }
        Result<Void> overlap = validateNoActiveOverlap(request.getRoomId(), request.getStartDate(), request.getEndDate(), currentId);
        if (overlap.getCode() != 200) {
            return overlap;
        }
        return Result.success();
    }

    private Result<Void> validateNoActiveOverlap(Long roomId, LocalDate startDate, LocalDate endDate, Long currentId) {
        boolean exists = contractRepository.findByRoomIdAndStatus(roomId, "ACTIVE")
                .stream()
                .filter(contract -> currentId == null || !contract.getId().equals(currentId))
                .anyMatch(contract -> rangesOverlap(startDate, endDate, contract.getStartDate(), contract.getEndDate()));
        return exists ? Result.error("同一房间同一时间段内已存在履约中合同") : Result.success();
    }

    private boolean rangesOverlap(LocalDate startA, LocalDate endA, LocalDate startB, LocalDate endB) {
        if (startA == null || endA == null || startB == null || endB == null) {
            return false;
        }
        return !startA.isAfter(endB) && !startB.isAfter(endA);
    }

    private Long resolveTenantId(ContractCreateRequest request) {
        if (request.getTenantId() != null && tenantRepository.existsById(request.getTenantId())) {
            return request.getTenantId();
        }
        String tenantName = request.getTenantName() == null ? "" : request.getTenantName().trim();
        if (tenantName.isBlank()) {
            return null;
        }
        Tenant tenant = tenantRepository.findFirstByTenantName(tenantName)
                .orElseGet(() -> {
                    Tenant created = new Tenant();
                    created.setTenantName(tenantName);
                    created.setContactPerson(request.getContactPerson());
                    created.setContactPhone(request.getContactPhone());
                    created.setContactEmail(request.getContactEmail());
                    created.setStatus("ACTIVE");
                    return tenantRepository.save(created);
                });
        return tenant.getId();
    }

    private void closeOccupancy(Contract contract, LocalDate checkoutDate) {
        occupancyRepository.findByContractIdAndStatus(contract.getId(), "ACTIVE").ifPresent(occupancy -> {
            occupancy.setStatus("ENDED");
            occupancy.setCheckoutDate(checkoutDate);
            occupancyRepository.save(occupancy);
        });
    }

    private void updateRoomStatus(Long roomId, String status) {
        roomRepository.findById(roomId).ifPresent(room -> {
            room.setStatus(status);
            roomRepository.save(room);
        });
    }

    private void writeEvent(Contract contract, String action, String beforeStatus, String afterStatus,
                            Long operatorId, String operatorName, String remark) {
        ContractEvent event = new ContractEvent();
        event.setContractId(contract.getId());
        event.setAction(action);
        event.setBeforeStatus(beforeStatus);
        event.setAfterStatus(afterStatus);
        event.setOperatorId(operatorId);
        event.setOperatorName(defaultText(operatorName, "system"));
        event.setRemark(remark);
        contractEventRepository.save(event);
    }

    private ContractResponse toResponse(Contract contract) {
        ContractResponse response = new ContractResponse();
        response.setId(contract.getId());
        response.setContractNumber(contract.getContractNumber());
        response.setContractName(contract.getContractName());
        response.setTenantId(contract.getTenantId());
        response.setRoomId(contract.getRoomId());
        response.setLeaseId(contract.getLeaseId());
        response.setStartDate(contract.getStartDate());
        response.setEndDate(contract.getEndDate());
        response.setRentAmount(defaultAmount(contract.getRentAmount()));
        response.setPropertyFeeAmount(defaultAmount(contract.getPropertyFeeAmount()));
        response.setDepositAmount(defaultAmount(contract.getDepositAmount()));
        response.setPaymentCycle(contract.getPaymentCycle());
        response.setBillingDay(contract.getBillingDay());
        response.setDueDay(contract.getDueDay());
        response.setPaymentTerms(contract.getPaymentTerms());
        response.setBillingLeadDays(resolveAdvanceBillDays(contract));
        response.setAdvanceBillDays(resolveAdvanceBillDays(contract));
        response.setBillGeneratedUntil(contract.getBillGeneratedUntil());
        response.setBillingRule(contract.getBillingRule());
        response.setStatus(contract.getStatus());
        response.setStatusText(statusText(contract.getStatus()));
        response.setTerminationDate(contract.getTerminationDate());
        response.setTerminationReason(contract.getTerminationReason());
        response.setTerminatedAt(contract.getTerminatedAt());
        response.setCancelledAt(contract.getCancelledAt());
        response.setRemark(contract.getRemark());
        response.setCreatedTime(contract.getCreatedTime());
        response.setUpdatedTime(contract.getUpdatedTime());
        return response;
    }

    private ContractEventResponse toEventResponse(ContractEvent event) {
        ContractEventResponse response = new ContractEventResponse();
        response.setId(event.getId());
        response.setContractId(event.getContractId());
        response.setAction(event.getAction());
        response.setActionText(actionText(event.getAction()));
        response.setBeforeStatus(event.getBeforeStatus());
        response.setAfterStatus(event.getAfterStatus());
        response.setOperatorId(event.getOperatorId());
        response.setOperatorName(event.getOperatorName());
        response.setRemark(event.getRemark());
        response.setCreatedTime(event.getCreatedTime());
        return response;
    }

    private int resolveAdvanceBillDays(Contract contract) {
        if (contract.getAdvanceBillDays() != null) {
            return contract.getAdvanceBillDays();
        }
        return contract.getBillingLeadDays() == null ? 7 : contract.getBillingLeadDays();
    }

    private String defaultBillingRule(String billingRule, int advanceDays) {
        return billingRule != null && !billingRule.isBlank()
                ? billingRule.trim()
                : "提前" + advanceDays + "天生成账单";
    }

    private String defaultPaymentCycle(String paymentCycle) {
        return paymentCycle == null || paymentCycle.isBlank() ? "MONTHLY" : paymentCycle.trim();
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean isNegative(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) < 0;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String statusText(String status) {
        return switch (status == null ? "" : status) {
            case "DRAFT" -> "草稿";
            case "PENDING" -> "待生效";
            case "ACTIVE" -> "履约中";
            case "EXPIRED" -> "已到期";
            case "TERMINATED" -> "已终止";
            case "CANCELLED" -> "已作废";
            default -> status;
        };
    }

    private String actionText(String action) {
        return switch (action == null ? "" : action) {
            case "CREATE" -> "创建合同";
            case "ACTIVATE" -> "合同生效";
            case "CHECK_IN" -> "办理入驻";
            case "GENERATE_BILL" -> "生成账单";
            case "TERMINATE" -> "合同终止";
            case "CANCEL" -> "合同作废";
            case "CHECK_OUT" -> "退租";
            default -> action;
        };
    }
}
