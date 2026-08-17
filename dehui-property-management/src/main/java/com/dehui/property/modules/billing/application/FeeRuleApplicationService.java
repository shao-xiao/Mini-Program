package com.dehui.property.modules.billing.application;

import com.dehui.property.common.BusinessException;
import com.dehui.property.common.PageResponse;
import com.dehui.property.modules.billing.domain.model.BillingCycle;
import com.dehui.property.modules.billing.domain.model.CalculationType;
import com.dehui.property.modules.billing.domain.model.FeeRuleStatus;
import com.dehui.property.modules.billing.domain.model.FeeRuleVersionStatus;
import com.dehui.property.modules.billing.domain.model.FeeType;
import com.dehui.property.modules.billing.domain.service.FeeRulePolicy;
import com.dehui.property.modules.billing.domain.service.FeeRuleRevisionPolicy;
import com.dehui.property.modules.billing.domain.service.FeeRuleActivationPolicy;
import com.dehui.property.modules.billing.dto.FeeRuleActionRequest;
import com.dehui.property.modules.billing.dto.FeeRuleCompatibilityRequest;
import com.dehui.property.modules.billing.dto.FeeRuleCreateRequest;
import com.dehui.property.modules.billing.dto.FeeRuleResponse;
import com.dehui.property.modules.billing.dto.FeeRuleSummaryResponse;
import com.dehui.property.modules.billing.dto.FeeRuleUpdateRequest;
import com.dehui.property.modules.billing.dto.FeeRuleVersionResponse;
import com.dehui.property.modules.billing.infrastructure.repository.FeeRuleRepository;
import com.dehui.property.modules.billing.infrastructure.repository.FeeRuleRepository.FeeRuleSnapshot;
import com.dehui.property.modules.billing.infrastructure.repository.FeeRuleRepository.FeeRuleVersionSnapshot;
import com.dehui.property.modules.shared.infrastructure.DomainEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeeRuleApplicationService {

    private final FeeRuleRepository repository;
    private final DomainEventRepository domainEvents;
    private final ObjectMapper objectMapper;

    public FeeRuleApplicationService(
            FeeRuleRepository repository,
            DomainEventRepository domainEvents,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.domainEvents = domainEvents;
        this.objectMapper = objectMapper;
    }

    public PageResponse<FeeRuleResponse> search(
            String keyword,
            String status,
            String feeType,
            String calculationType,
            String sortField,
            String sortDirection,
            Integer page,
            Integer pageSize
    ) {
        return search(
                keyword, status, feeType, calculationType,
                sortField, sortDirection, null, null, null, page, pageSize
        );
    }

    public PageResponse<FeeRuleResponse> search(
            String keyword,
            String status,
            String feeType,
            String calculationType,
            String sortField,
            String sortDirection,
            String sorts,
            String groupBy,
            String customFilters,
            Integer page,
            Integer pageSize
    ) {
        return repository.search(
                keyword,
                parse(status, FeeRuleStatus.class, "status"),
                parse(feeType, FeeType.class, "feeType"),
                parse(calculationType, CalculationType.class, "calculationType"),
                sortField,
                sortDirection,
                sorts,
                groupBy,
                customFilters,
                page,
                pageSize
        );
    }

    public PageResponse<FeeRuleResponse> search(
            String keyword,
            String status,
            String feeType,
            String calculationType,
            String sortField,
            String sortDirection,
            String sorts,
            String groupBy,
            Integer page,
            Integer pageSize
    ) {
        return search(
                keyword, status, feeType, calculationType, sortField, sortDirection,
                sorts, groupBy, null, page, pageSize
        );
    }

    public FeeRuleSummaryResponse summary(
            String keyword,
            String status,
            String feeType,
            String calculationType
    ) {
        return summary(keyword, status, feeType, calculationType, null);
    }

    public FeeRuleSummaryResponse summary(
            String keyword,
            String status,
            String feeType,
            String calculationType,
            String customFilters
    ) {
        return repository.summary(
                keyword,
                parse(status, FeeRuleStatus.class, "status"),
                parse(feeType, FeeType.class, "feeType"),
                parse(calculationType, CalculationType.class, "calculationType"),
                customFilters
        );
    }

    public FeeRuleResponse find(Long id) {
        return repository.find(id);
    }

    public List<FeeRuleVersionResponse> versions(Long id) {
        return repository.versions(id);
    }

    @Transactional
    public FeeRuleResponse create(FeeRuleCreateRequest request, Long operatorId) {
        validate(request.calculationType(), request.billingCycle(), request.effectiveFrom(), request.effectiveTo(),
                request.fixedAmount(), request.unitPrice());
        Long id = repository.create(request, operatorId);
        FeeRuleResponse created = repository.find(id);
        record(id, "CREATE", null, created, operatorId, "FeeRuleCreated");
        return created;
    }

    @Transactional
    public FeeRuleResponse createCompatibility(FeeRuleCompatibilityRequest request, Long operatorId) {
        FeeRuleCreateRequest converted = new FeeRuleCreateRequest(
                null,
                request.ruleName(),
                request.feeType(),
                CalculationType.FIXED,
                request.cycle(),
                request.startDate(),
                request.endDate(),
                request.amount(),
                null,
                "CNY",
                request.remark()
        );
        return create(converted, operatorId);
    }

    @Transactional
    public FeeRuleResponse update(Long id, FeeRuleUpdateRequest request, Long operatorId) {
        validate(request.calculationType(), request.billingCycle(), request.effectiveFrom(), request.effectiveTo(),
                request.fixedAmount(), request.unitPrice());
        FeeRuleSnapshot current = repository.lock(id);
        FeeRuleRevisionPolicy.requireVersion(request.version(), current.version());
        FeeRuleResponse before = repository.find(id);
        if (current.activeVersionId() != null) {
            FeeRuleVersionSnapshot activeVersion = repository.versionSnapshot(current.activeVersionId());
            FeeRuleRevisionPolicy.requireRevisionAfter(activeVersion.effectiveFrom(), request.effectiveFrom());
        }
        repository.addRevision(current, request, operatorId);
        FeeRuleResponse updated = repository.find(id);
        record(id, "CREATE_REVISION", before, updated, operatorId, "FeeRuleVersionDrafted");
        return updated;
    }

    @Transactional
    public FeeRuleResponse activate(Long id, FeeRuleActionRequest request, Long operatorId) {
        FeeRuleSnapshot current = repository.lock(id);
        FeeRuleRevisionPolicy.requireVersion(request.version(), current.version());
        FeeRuleVersionSnapshot version = repository.versionSnapshot(current.currentVersionId());
        if (version.status() != FeeRuleVersionStatus.DRAFT) {
            throw new BusinessException(409, "只有最新草稿版本可以生效");
        }
        LocalDate today = LocalDate.now();
        FeeRuleActivationPolicy.requirePublishable(version.effectiveFrom(), version.effectiveTo(), today);
        FeeRuleResponse before = repository.find(id);
        repository.activate(current, operatorId);
        FeeRuleResponse updated = repository.find(id);
        record(id, "ACTIVATE_VERSION", before, updated, operatorId, "FeeRuleVersionActivated");
        return updated;
    }

    @Transactional
    public FeeRuleResponse disable(Long id, FeeRuleActionRequest request, Long operatorId) {
        FeeRuleSnapshot current = repository.lock(id);
        FeeRuleRevisionPolicy.requireVersion(request.version(), current.version());
        if (current.status() != FeeRuleStatus.ACTIVE) {
            throw new BusinessException(409, "只有生效中的收费规则可以停用");
        }
        FeeRuleResponse before = repository.find(id);
        repository.disable(current, operatorId);
        FeeRuleResponse updated = repository.find(id);
        record(id, "DISABLE", before, updated, operatorId, "FeeRuleDisabled");
        return updated;
    }

    private void validate(
            CalculationType calculationType,
            BillingCycle billingCycle,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            java.math.BigDecimal fixedAmount,
            java.math.BigDecimal unitPrice
    ) {
        FeeRulePolicy.validate(calculationType, billingCycle, effectiveFrom, effectiveTo, fixedAmount, unitPrice);
    }

    private <E extends Enum<E>> E parse(String raw, Class<E> type, String field) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(400, field + " 取值无效");
        }
    }

    private void record(Long id, String action, Object before, Object after, Long operatorId, String eventType) {
        String beforeJson = json(before);
        String afterJson = json(after);
        domainEvents.audit("FeeRule", id, action, beforeJson, afterJson, operatorId, null);
        domainEvents.outbox("FeeRule", id, eventType, afterJson);
    }

    private String json(Object value) {
        if (value == null) return "{}";
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "收费规则审计快照序列化失败");
        }
    }
}
