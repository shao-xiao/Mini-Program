package com.dehui.property.modules.contract.service;

import com.dehui.property.common.Result;
import com.dehui.property.modules.contract.dto.ContractCreateRequest;
import com.dehui.property.modules.contract.dto.ContractResponse;
import com.dehui.property.modules.contract.entity.Contract;
import com.dehui.property.modules.contract.repository.ContractRepository;
import com.dehui.property.modules.tenant.entity.Tenant;
import com.dehui.property.modules.lease.repository.RoomLeaseRepository;
import com.dehui.property.modules.tenant.repository.TenantRepository;
import com.dehui.property.modules.tenant.service.TenantContactService;
import com.dehui.property.modules.building.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContractService {
    private final ContractRepository contractRepository;
    private final TenantRepository tenantRepository;
    private final RoomRepository roomRepository;
    private final RoomLeaseRepository roomLeaseRepository;
    private final ContractBillGenerationService contractBillGenerationService;
    private final TenantContactService tenantContactService;

    public Result<List<ContractResponse>> findAll() {
        List<ContractResponse> responses = contractRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return Result.success(responses);
    }

    public Result<ContractResponse> findById(Long id) {
        return contractRepository.findById(id)
                .map(contract -> Result.success(toResponse(contract)))
                .orElseGet(() -> Result.error("合同不存在"));
    }

    @Transactional
    public Result<ContractResponse> create(ContractCreateRequest request) {
        // 1. 校验合同编号唯一
        if (contractRepository.existsByContractNumber(request.getContractNumber())) {
            return Result.error("合同编号已存在");
        }

        Long tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Result.error("请输入租户名称");
        }

        // 3. 校验房间存在
        var room = roomRepository.findById(request.getRoomId()).orElse(null);
        if (room == null) {
            return Result.error("房间不存在");
        }

        // 4. 合同签订阶段只允许选择可租房间，后续再办理入驻。
        if (!"AVAILABLE".equals(room.getStatus())) {
            return Result.error("房间当前不可租，状态为：" + room.getStatus());
        }

        // 5. 如传入租约，则校验租约未被其他合同占用；新流程下租约可为空。
        if (request.getLeaseId() != null && contractRepository.existsByLeaseId(request.getLeaseId())) {
            return Result.error("该租约已绑定其他合同");
        }

        // 6. 创建合同
        Contract contract = new Contract();
        contract.setContractNumber(request.getContractNumber());
        contract.setContractName(request.getContractName());
        contract.setTenantId(tenantId);
        contract.setRoomId(request.getRoomId());
        contract.setLeaseId(request.getLeaseId());
        contract.setStartDate(request.getStartDate());
        contract.setEndDate(request.getEndDate());
        contract.setRentAmount(request.getRentAmount());
        contract.setPropertyFeeAmount(request.getPropertyFeeAmount() != null ? request.getPropertyFeeAmount() : java.math.BigDecimal.ZERO);
        contract.setDepositAmount(request.getDepositAmount() != null ? request.getDepositAmount() : java.math.BigDecimal.ZERO);
        contract.setPaymentCycle(request.getPaymentCycle() != null ? request.getPaymentCycle() : "MONTHLY");
        contract.setBillingDay(request.getBillingDay() != null ? request.getBillingDay() : 1);
        contract.setDueDay(request.getDueDay() != null ? request.getDueDay() : 10);
        contract.setPaymentTerms(request.getPaymentTerms());
        contract.setBillingLeadDays(request.getBillingLeadDays() != null ? request.getBillingLeadDays() : 7);
        contract.setBillingRule(request.getBillingRule() != null && !request.getBillingRule().isBlank()
                ? request.getBillingRule()
                : String.format("账期开始前%d日出账；若遇周末，则提前至工作日", contract.getBillingLeadDays()));
        contract.setStatus("DRAFT");
        contract.setRemark(request.getRemark());
        Contract saved = contractRepository.save(contract);
        tenantContactService.syncPrimaryContact(tenantId, request.getContactPerson(), request.getContactPhone());

        return Result.success(toResponse(saved));
    }

    @Transactional
    public Result<List<ContractResponse>> findActivePendingCheckin() {
        List<ContractResponse> responses = contractRepository.findByStatusAndLeaseIdIsNull("ACTIVE")
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return Result.success(responses);
    }

    @Transactional
    public Result<ContractResponse> activate(Long id) {
        return contractRepository.findById(id)
                .map(contract -> {
                    if ("ACTIVE".equals(contract.getStatus())) {
                        return Result.<ContractResponse>error("合同已生效");
                    }
                    if ("TERMINATED".equals(contract.getStatus())) {
                        return Result.<ContractResponse>error("合同已终止，无法生效");
                    }
                    contract.setStatus("ACTIVE");
                    Contract saved = contractRepository.save(contract);
                    contractBillGenerationService.generateDueBillsForContract(saved, LocalDate.now());
                    return Result.success(toResponse(saved));
                })
                .orElseGet(() -> Result.error("合同不存在"));
    }

    @Transactional
    public Result<ContractResponse> terminate(Long id) {
        return contractRepository.findById(id)
                .map(contract -> {
                    if ("TERMINATED".equals(contract.getStatus())) {
                        return Result.<ContractResponse>error("合同已终止");
                    }
                    contract.setStatus("TERMINATED");
                    Contract saved = contractRepository.save(contract);
                    return Result.success(toResponse(saved));
                })
                .orElseGet(() -> Result.error("合同不存在"));
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
        response.setRentAmount(contract.getRentAmount());
        response.setPropertyFeeAmount(contract.getPropertyFeeAmount());
        response.setDepositAmount(contract.getDepositAmount());
        response.setPaymentCycle(contract.getPaymentCycle());
        response.setBillingDay(contract.getBillingDay());
        response.setDueDay(contract.getDueDay());
        response.setPaymentTerms(contract.getPaymentTerms());
        response.setBillingLeadDays(contract.getBillingLeadDays() != null ? contract.getBillingLeadDays() : 7);
        response.setBillingRule(contract.getBillingRule());
        response.setStatus(contract.getStatus());
        response.setRemark(contract.getRemark());
        response.setCreatedTime(contract.getCreatedTime());
        response.setUpdatedTime(contract.getUpdatedTime());
        return response;
    }

    private Long resolveTenantId(ContractCreateRequest request) {
        if (request.getTenantId() != null && tenantRepository.existsById(request.getTenantId())) {
            return request.getTenantId();
        }

        if (request.getTenantName() == null || request.getTenantName().isBlank()) {
            return null;
        }

        String tenantName = request.getTenantName().trim();
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
}
