package com.dehui.property.modules.contract.service;

import com.dehui.property.common.Result;
import com.dehui.property.modules.contract.dto.ContractCreateRequest;
import com.dehui.property.modules.contract.dto.ContractResponse;
import com.dehui.property.modules.contract.entity.Contract;
import com.dehui.property.modules.contract.repository.ContractRepository;
import com.dehui.property.modules.lease.repository.RoomLeaseRepository;
import com.dehui.property.modules.tenant.repository.TenantRepository;
import com.dehui.property.modules.building.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContractService {
    private final ContractRepository contractRepository;
    private final TenantRepository tenantRepository;
    private final RoomRepository roomRepository;
    private final RoomLeaseRepository roomLeaseRepository;

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

        // 2. 校验租户存在
        if (!tenantRepository.existsById(request.getTenantId())) {
            return Result.error("租户不存在");
        }

        // 3. 校验房间存在
        if (!roomRepository.existsById(request.getRoomId())) {
            return Result.error("房间不存在");
        }

        // 4. 校验租约存在且为 ACTIVE 状态
        roomLeaseRepository.findById(request.getLeaseId())
                .filter(lease -> "ACTIVE".equals(lease.getStatus()))
                .orElseGet(() -> null);
        if (request.getLeaseId() == null ||
            roomLeaseRepository.findById(request.getLeaseId()).isEmpty() ||
            !"ACTIVE".equals(roomLeaseRepository.findById(request.getLeaseId()).get().getStatus())) {
            return Result.error("租约不存在或未生效");
        }

        // 5. 校验租约未被其他合同占用
        if (contractRepository.existsByLeaseId(request.getLeaseId())) {
            return Result.error("该租约已绑定其他合同");
        }

        // 6. 创建合同
        Contract contract = new Contract();
        contract.setContractNumber(request.getContractNumber());
        contract.setContractName(request.getContractName());
        contract.setTenantId(request.getTenantId());
        contract.setRoomId(request.getRoomId());
        contract.setLeaseId(request.getLeaseId());
        contract.setStartDate(request.getStartDate());
        contract.setEndDate(request.getEndDate());
        contract.setRentAmount(request.getRentAmount());
        contract.setDepositAmount(request.getDepositAmount() != null ? request.getDepositAmount() : java.math.BigDecimal.ZERO);
        contract.setPaymentCycle(request.getPaymentCycle() != null ? request.getPaymentCycle() : "MONTHLY");
        contract.setStatus("DRAFT");
        contract.setRemark(request.getRemark());
        Contract saved = contractRepository.save(contract);

        return Result.success(toResponse(saved));
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
        response.setDepositAmount(contract.getDepositAmount());
        response.setPaymentCycle(contract.getPaymentCycle());
        response.setStatus(contract.getStatus());
        response.setRemark(contract.getRemark());
        response.setCreatedTime(contract.getCreatedTime());
        response.setUpdatedTime(contract.getUpdatedTime());
        return response;
    }
}
