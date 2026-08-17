package com.dehui.property.modules.billing.application;

import com.dehui.property.common.BusinessException;
import com.dehui.property.common.PageResponse;
import com.dehui.property.modules.billing.domain.model.BillingPlanStatus;
import com.dehui.property.modules.billing.domain.model.CalculationType;
import com.dehui.property.modules.billing.domain.model.FeeRuleStatus;
import com.dehui.property.modules.billing.domain.model.FeeRuleVersionStatus;
import com.dehui.property.modules.billing.domain.service.BillingPlanPolicy;
import com.dehui.property.modules.billing.domain.service.FeeAmountCalculator;
import com.dehui.property.modules.billing.dto.BillingPlanActionRequest;
import com.dehui.property.modules.billing.dto.BillingPlanCreateRequest;
import com.dehui.property.modules.billing.dto.BillingPlanItemRequest;
import com.dehui.property.modules.billing.dto.BillingPlanItemResponse;
import com.dehui.property.modules.billing.dto.BillingPlanResponse;
import com.dehui.property.modules.billing.dto.BillingPlanSummaryResponse;
import com.dehui.property.modules.billing.dto.BillingPlanUpdateRequest;
import com.dehui.property.modules.billing.infrastructure.repository.BillingPlanRepository;
import com.dehui.property.modules.billing.infrastructure.repository.BillingPlanRepository.BillingPlanSnapshot;
import com.dehui.property.modules.billing.infrastructure.repository.BillingPlanRepository.ContractLeaseBounds;
import com.dehui.property.modules.billing.infrastructure.repository.BillingPlanRepository.ResolvedBillingPlanItem;
import com.dehui.property.modules.billing.infrastructure.repository.FeeRuleRepository;
import com.dehui.property.modules.billing.infrastructure.repository.FeeRuleRepository.FeeRuleVersionSnapshot;
import com.dehui.property.modules.shared.infrastructure.DomainEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingPlanApplicationService {

    private static final Set<String> TERMINAL_LEASE_STATUSES = Set.of("TERMINATED", "CANCELLED");

    private final BillingPlanRepository repository;
    private final FeeRuleRepository feeRuleRepository;
    private final DomainEventRepository domainEvents;
    private final ObjectMapper objectMapper;

    public BillingPlanApplicationService(
            BillingPlanRepository repository,
            FeeRuleRepository feeRuleRepository,
            DomainEventRepository domainEvents,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.feeRuleRepository = feeRuleRepository;
        this.domainEvents = domainEvents;
        this.objectMapper = objectMapper;
    }

    public PageResponse<BillingPlanResponse> search(
            String keyword,
            String status,
            Long tenantId,
            Long contractId,
            Long leaseId,
            String sortField,
            String sortDirection,
            Integer page,
            Integer pageSize
    ) {
        return search(
                keyword, status, tenantId, contractId, leaseId,
                sortField, sortDirection, null, null, null, page, pageSize
        );
    }

    public PageResponse<BillingPlanResponse> search(
            String keyword,
            String status,
            Long tenantId,
            Long contractId,
            Long leaseId,
            String sortField,
            String sortDirection,
            String sorts,
            String groupBy,
            String customFilters,
            Integer page,
            Integer pageSize
    ) {
        return repository.search(keyword, parseStatus(status), tenantId, contractId, leaseId,
                sortField, sortDirection, sorts, groupBy, customFilters, page, pageSize);
    }

    public PageResponse<BillingPlanResponse> search(
            String keyword,
            String status,
            Long tenantId,
            Long contractId,
            Long leaseId,
            String sortField,
            String sortDirection,
            String sorts,
            String groupBy,
            Integer page,
            Integer pageSize
    ) {
        return search(
                keyword, status, tenantId, contractId, leaseId, sortField, sortDirection,
                sorts, groupBy, null, page, pageSize
        );
    }

    public BillingPlanSummaryResponse summary(
            String keyword,
            String status,
            Long tenantId,
            Long contractId,
            Long leaseId
    ) {
        return summary(keyword, status, tenantId, contractId, leaseId, null);
    }

    public BillingPlanSummaryResponse summary(
            String keyword,
            String status,
            Long tenantId,
            Long contractId,
            Long leaseId,
            String customFilters
    ) {
        return repository.summary(keyword, parseStatus(status), tenantId, contractId, leaseId, customFilters);
    }

    public BillingPlanResponse find(Long id) {
        return repository.find(id);
    }

    @Transactional
    public BillingPlanResponse create(BillingPlanCreateRequest request, Long operatorId) {
        validateBounds(request.tenantId(), request.contractId(), request.leaseId(),
                request.effectiveFrom(), request.effectiveTo());
        List<ResolvedBillingPlanItem> items = resolveItems(
                request.items(), request.contractId(), request.billingCycle(), request.currency(),
                request.effectiveFrom(), request.effectiveTo()
        );
        Long id = repository.create(request, items, operatorId);
        BillingPlanResponse created = repository.find(id);
        record(id, "CREATE", null, created, operatorId, "BillingPlanCreated");
        return created;
    }

    @Transactional
    public BillingPlanResponse update(Long id, BillingPlanUpdateRequest request, Long operatorId) {
        BillingPlanSnapshot current = repository.lock(id);
        BillingPlanPolicy.requireVersion(request.version(), current.version());
        if (current.status() != BillingPlanStatus.DRAFT) {
            throw new BusinessException(409, "只有草稿计费计划可以编辑；生效计划请终止后新建");
        }
        validateBounds(request.tenantId(), request.contractId(), request.leaseId(),
                request.effectiveFrom(), request.effectiveTo());
        List<ResolvedBillingPlanItem> items = resolveItems(
                request.items(), request.contractId(), request.billingCycle(), request.currency(),
                request.effectiveFrom(), request.effectiveTo()
        );
        BillingPlanResponse before = repository.find(id);
        repository.update(current, request, items, operatorId);
        BillingPlanResponse updated = repository.find(id);
        record(id, "UPDATE_DRAFT", before, updated, operatorId, "BillingPlanDraftUpdated");
        return updated;
    }

    @Transactional
    public BillingPlanResponse activate(Long id, BillingPlanActionRequest request, Long operatorId) {
        BillingPlanSnapshot current = repository.lock(id);
        BillingPlanPolicy.requireVersion(request.version(), current.version());
        if (current.status() != BillingPlanStatus.DRAFT) {
            throw new BusinessException(409, "只有草稿计费计划可以激活");
        }
        BillingPlanResponse before = repository.find(id);
        validateBounds(before.tenantId(), before.contractId(), before.leaseId(),
                before.effectiveFrom(), before.effectiveTo());
        validateExistingItems(before);
        repository.activate(current, operatorId);
        BillingPlanResponse updated = repository.find(id);
        record(id, "ACTIVATE", before, updated, operatorId, "BillingPlanActivated");
        return updated;
    }

    @Transactional
    public BillingPlanResponse terminate(Long id, BillingPlanActionRequest request, Long operatorId) {
        BillingPlanSnapshot current = repository.lock(id);
        BillingPlanPolicy.requireVersion(request.version(), current.version());
        if (current.status() != BillingPlanStatus.ACTIVE) {
            throw new BusinessException(409, "只有生效中的计费计划可以终止");
        }
        BillingPlanResponse before = repository.find(id);
        repository.terminate(current, request.reason(), operatorId);
        BillingPlanResponse updated = repository.find(id);
        record(id, "TERMINATE", before, updated, operatorId, "BillingPlanTerminated");
        return updated;
    }

    private void validateBounds(
            Long tenantId,
            Long contractId,
            Long leaseId,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) {
        ContractLeaseBounds bounds = repository.relationBounds(tenantId, contractId, leaseId);
        BillingPlanPolicy.requireInside(effectiveFrom, effectiveTo,
                bounds.contractStart(), bounds.contractEnd(), "合同");
        if (leaseId != null) {
            if (TERMINAL_LEASE_STATUSES.contains(bounds.leaseStatus())) {
                throw new BusinessException(409, "终态租约不能新建或激活计费计划");
            }
            BillingPlanPolicy.requireInside(effectiveFrom, effectiveTo,
                    bounds.leaseStart(), bounds.leaseEnd(), "租约");
        }
    }

    private List<ResolvedBillingPlanItem> resolveItems(
            List<BillingPlanItemRequest> requests,
            Long contractId,
            com.dehui.property.modules.billing.domain.model.BillingCycle billingCycle,
            String currency,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) {
        List<ResolvedBillingPlanItem> resolved = new ArrayList<>();
        Set<Long> uniqueVersionIds = new HashSet<>();
        for (int index = 0; index < requests.size(); index++) {
            BillingPlanItemRequest item = requests.get(index);
            if (!uniqueVersionIds.add(item.feeRuleVersionId())) {
                throw new BusinessException(400, "同一收费规则版本不能在一个计费计划中重复");
            }
            FeeRuleVersionSnapshot version = feeRuleRepository.versionSnapshot(item.feeRuleVersionId());
            requireSelectable(version, billingCycle, currency, effectiveFrom, effectiveTo);

            CalculationType type = version.calculationType();
            BigDecimal quantity = null;
            BigDecimal unitPrice = null;
            BigDecimal fixedAmount = null;
            if (type == CalculationType.FIXED || type == CalculationType.ONE_TIME) {
                fixedAmount = item.fixedAmount() == null ? version.fixedAmount() : item.fixedAmount();
            } else {
                quantity = version.feeType() == com.dehui.property.modules.billing.domain.model.FeeType.PROPERTY
                        && type == CalculationType.AREA_UNIT_PRICE
                        ? repository.propertyFeeArea(contractId)
                        : item.quantity();
                unitPrice = item.unitPrice() == null ? version.unitPrice() : item.unitPrice();
            }
            BigDecimal amount = FeeAmountCalculator.calculate(type, quantity, unitPrice, fixedAmount);
            String itemName = item.itemName() == null || item.itemName().isBlank()
                    ? version.ruleName()
                    : item.itemName().trim();
            resolved.add(new ResolvedBillingPlanItem(
                    version.id(), itemName, quantity, unitPrice, fixedAmount, amount,
                    item.sortOrder() == null ? index : item.sortOrder()
            ));
        }
        return resolved;
    }

    private void validateExistingItems(BillingPlanResponse plan) {
        for (BillingPlanItemResponse item : plan.items()) {
            FeeRuleVersionSnapshot version = feeRuleRepository.versionSnapshot(item.feeRuleVersionId());
            requireSelectable(version, plan.billingCycle(), plan.currency(), plan.effectiveFrom(), plan.effectiveTo());
        }
    }

    private void requireSelectable(
            FeeRuleVersionSnapshot version,
            com.dehui.property.modules.billing.domain.model.BillingCycle billingCycle,
            String currency,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) {
        if (version.ruleStatus() != FeeRuleStatus.ACTIVE
                || version.status() != FeeRuleVersionStatus.ACTIVE
                || !version.id().equals(version.activeVersionId())) {
            throw new BusinessException(409, "计费计划只能引用收费规则当前生效版本");
        }
        if (version.billingCycle() != billingCycle) {
            throw new BusinessException(400, "计费计划周期必须与收费规则版本周期一致");
        }
        if (!version.currency().equalsIgnoreCase(currency)) {
            throw new BusinessException(400, "计费计划与收费规则版本币种必须一致");
        }
        if (effectiveFrom.isBefore(version.effectiveFrom())
                || (version.effectiveTo() != null && effectiveTo.isAfter(version.effectiveTo()))) {
            throw new BusinessException(400, "计费计划区间超出收费规则版本有效期");
        }
    }

    private BillingPlanStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return BillingPlanStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(400, "status 取值无效");
        }
    }

    private void record(Long id, String action, Object before, Object after, Long operatorId, String eventType) {
        String beforeJson = json(before);
        String afterJson = json(after);
        domainEvents.audit("BillingPlan", id, action, beforeJson, afterJson, operatorId, null);
        domainEvents.outbox("BillingPlan", id, eventType, afterJson);
    }

    private String json(Object value) {
        if (value == null) return "{}";
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "计费计划审计快照序列化失败");
        }
    }
}
