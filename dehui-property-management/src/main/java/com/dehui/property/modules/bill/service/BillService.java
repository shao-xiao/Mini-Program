package com.dehui.property.modules.bill.service;

import com.dehui.property.common.Result;
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
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillService {
    private final BillRepository billRepository;
    private final ContractRepository contractRepository;
    private final TenantRepository tenantRepository;

    public Result<List<BillResponse>> findAll() {
        List<BillResponse> responses = billRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return Result.success(responses);
    }

    public Result<List<BillResponse>> findByTenantId(Long tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            return Result.error("租户不存在");
        }
        List<BillResponse> responses = billRepository.findByTenantIdOrderByCreatedTimeDesc(tenantId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return Result.success(responses);
    }

    public Result<List<BillResponse>> findByContractId(Long contractId) {
        if (!contractRepository.existsById(contractId)) {
            return Result.error("合同不存在");
        }
        List<BillResponse> responses = billRepository.findByContractIdOrderByPeriodStartDesc(contractId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return Result.success(responses);
    }

    public Result<List<BillResponse>> findByStatus(String status) {
        List<BillResponse> responses = billRepository.findByStatusOrderByDueDateAsc(status)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return Result.success(responses);
    }

    @Transactional
    public Result<BillResponse> create(BillCreateRequest request) {
        // 1. 校验账单编号唯一
        if (billRepository.existsByBillNumber(request.getBillNumber())) {
            return Result.error("账单编号已存在");
        }

        // 2. 校验租户存在
        if (!tenantRepository.existsById(request.getTenantId())) {
            return Result.error("租户不存在");
        }

        // 3. 校验合同存在
        Contract contract = contractRepository.findById(request.getContractId())
                .orElse(null);
        if (contract == null) {
            return Result.error("合同不存在");
        }

        // 4. 校验合同状态为 ACTIVE
        if (!"ACTIVE".equals(contract.getStatus())) {
            return Result.error("合同未生效，无法创建账单");
        }

        // 5. 校验租户与合同关联一致
        if (!request.getTenantId().equals(contract.getTenantId())) {
            return Result.error("租户与合同不匹配");
        }

        // 6. 防重复：同一合同 + 同一账期 + 同一类型不允许重复
        if (billRepository.existsByContractIdAndBillTypeAndPeriodStart(request.getContractId(), request.getBillType(), request.getPeriodStart())) {
            return Result.error("该合同在本账期内已有同类型账单");
        }

        // 7. 校验金额 > 0（@DecimalMin 已在前端校验，此处做兜底）
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return Result.error("账单金额必须大于0");
        }

        // 8. 校验到期日 >= 账期开始日
        if (request.getDueDate().isBefore(request.getPeriodStart())) {
            return Result.error("到期日不能早于账期开始日");
        }

        // 创建账单
        Bill bill = new Bill();
        bill.setBillNumber(request.getBillNumber());
        bill.setTenantId(request.getTenantId());
        bill.setContractId(request.getContractId());
        bill.setBillType(request.getBillType());
        bill.setPeriodStart(request.getPeriodStart());
        bill.setPeriodEnd(request.getPeriodEnd());
        bill.setAmount(request.getAmount());
        bill.setPaidAmount(BigDecimal.ZERO);
        bill.setDueDate(request.getDueDate());
        bill.setStatus("UNPAID");
        Bill saved = billRepository.save(bill);
        log.info("账单已创建: id={}, number={}", saved.getId(), saved.getBillNumber());

        return Result.success(toResponse(saved));
    }

    @Transactional
    public Result<BillResponse> pay(Long id) {
        return billRepository.findById(id)
                .map(bill -> {
                    if ("PAID".equals(bill.getStatus())) {
                        return Result.<BillResponse>error("账单已支付");
                    }
                    bill.setPaidAmount(bill.getAmount());
                    bill.setStatus("PAID");
                    Bill saved = billRepository.save(bill);
                    log.info("账单已支付: id={}, number={}", saved.getId(), saved.getBillNumber());
                    return Result.success(toResponse(saved));
                })
                .orElseGet(() -> Result.error("账单不存在"));
    }

    private BillResponse toResponse(Bill bill) {
        BillResponse response = new BillResponse();
        response.setId(bill.getId());
        response.setBillNumber(bill.getBillNumber());
        response.setTenantId(bill.getTenantId());
        tenantRepository.findById(bill.getTenantId())
                .ifPresent(tenant -> response.setTenantName(tenant.getTenantName()));
        response.setContractId(bill.getContractId());
        contractRepository.findById(bill.getContractId())
                .ifPresent(contract -> {
                    response.setContractNumber(contract.getContractNumber());
                    response.setContractName(contract.getContractName());
                });
        response.setBillType(bill.getBillType());
        response.setPeriodStart(bill.getPeriodStart());
        response.setPeriodEnd(bill.getPeriodEnd());
        response.setAmount(bill.getAmount());
        response.setPaidAmount(bill.getPaidAmount());
        response.setDueDate(bill.getDueDate());
        response.setStatus(bill.getStatus());
        response.setCreatedTime(bill.getCreatedTime());
        response.setUpdatedTime(bill.getUpdatedTime());
        return response;
    }
}
