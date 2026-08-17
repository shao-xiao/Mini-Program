package com.dehui.property.modules.contract.application;

import com.dehui.property.common.BusinessException;
import com.dehui.property.common.PageResponse;
import com.dehui.property.modules.contract.domain.model.ContractStatus;
import com.dehui.property.modules.contract.domain.service.ContractStateMachine;
import com.dehui.property.modules.contract.dto.ContractCreateRequest;
import com.dehui.property.modules.contract.dto.ContractHistoryResponse;
import com.dehui.property.modules.contract.dto.ContractResponse;
import com.dehui.property.modules.contract.dto.ContractSummaryResponse;
import com.dehui.property.modules.contract.dto.ContractTransitionRequest;
import com.dehui.property.modules.contract.dto.ContractUpdateRequest;
import com.dehui.property.modules.contract.infrastructure.repository.ContractRepository;
import com.dehui.property.modules.contract.infrastructure.repository.ContractRepository.ContractSnapshot;
import com.dehui.property.modules.shared.infrastructure.DomainEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractApplicationService {

    private final ContractRepository repository;
    private final DomainEventRepository domainEvents;
    private final ObjectMapper objectMapper;

    public ContractApplicationService(
            ContractRepository repository,
            DomainEventRepository domainEvents,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.domainEvents = domainEvents;
        this.objectMapper = objectMapper;
    }

    public PageResponse<ContractResponse> search(
            String keyword,
            String status,
            Long tenantId,
            LocalDate endDateFrom,
            LocalDate endDateTo,
            String sortField,
            String sortDirection,
            Integer page,
            Integer pageSize
    ) {
        return repository.search(keyword, status, tenantId, endDateFrom, endDateTo,
                sortField, sortDirection, page, pageSize);
    }

    public ContractSummaryResponse summary(String keyword, String status, Long tenantId) {
        return repository.summary(keyword, status, tenantId);
    }

    public List<ContractHistoryResponse> history(Long contractId) {
        return repository.history(contractId);
    }

    @Transactional
    public ContractResponse create(ContractCreateRequest request, Long operatorId) {
        validateDates(request.startDate(), request.endDate());
        repository.ensureTenantAndRooms(request.tenantId(), request.roomIds());
        Long id = repository.create(request, operatorId);
        repository.replaceRooms(id, request.roomIds(), operatorId);
        repository.addRevision(
                id,
                1,
                request.rentAmount(),
                request.depositAmount(),
                request.currency(),
                request.effectiveDate() == null ? request.startDate() : request.effectiveDate(),
                "合同创建",
                operatorId
        );
        repository.addStatusHistory(id, null, ContractStatus.DRAFT, "合同创建", operatorId, 0, null);
        ContractResponse created = repository.find(id);
        record("Contract", id, "CREATE", null, created, operatorId, "ContractCreated");
        return created;
    }

    @Transactional
    public ContractResponse update(Long id, ContractUpdateRequest request, Long operatorId) {
        validateDates(request.startDate(), request.endDate());
        ContractSnapshot current = repository.lock(id);
        if (current.status() != ContractStatus.DRAFT) {
            throw new BusinessException(409, "仅草稿合同允许修改商务条款");
        }
        if (request.version() != current.version()) {
            throw new BusinessException(409, "合同版本已变化，请刷新后重试");
        }
        repository.ensureTenantAndRooms(request.tenantId(), request.roomIds());
        repository.update(current, request, operatorId);
        repository.replaceRooms(id, request.roomIds(), operatorId);
        repository.addRevision(
                id,
                current.currentRevisionNo() + 1,
                request.rentAmount(),
                request.depositAmount(),
                request.currency(),
                request.effectiveDate() == null ? request.startDate() : request.effectiveDate(),
                request.changeReason(),
                operatorId
        );
        ContractResponse updated = repository.find(id);
        record("Contract", id, "REVISE", current, updated, operatorId, "ContractRevised");
        return updated;
    }

    @Transactional
    public ContractResponse submit(Long id, ContractTransitionRequest request, Long operatorId) {
        return transition(id, request, operatorId, ContractStatus.PENDING_APPROVAL, "ContractSubmitted");
    }

    @Transactional
    public ContractResponse approve(Long id, ContractTransitionRequest request, Long operatorId) {
        return transition(id, request, operatorId, ContractStatus.ACTIVE, "ContractApproved");
    }

    @Transactional
    public ContractResponse activate(Long id, ContractTransitionRequest request, Long operatorId) {
        return transition(id, request, operatorId, ContractStatus.ACTIVE, "ContractActivated");
    }

    @Transactional
    public ContractResponse terminate(Long id, ContractTransitionRequest request, Long operatorId) {
        return transition(id, request, operatorId, ContractStatus.TERMINATED, "ContractTerminated");
    }

    @Transactional
    public ContractResponse cancel(Long id, ContractTransitionRequest request, Long operatorId) {
        return transition(id, request, operatorId, ContractStatus.CANCELLED, "ContractCancelled");
    }

    private ContractResponse transition(
            Long id,
            ContractTransitionRequest request,
            Long operatorId,
            ContractStatus target,
            String eventType
    ) {
        ContractSnapshot current = repository.lock(id);
        if (request.version() != null && request.version() != current.version()) {
            throw new BusinessException(409, "合同版本已变化，请刷新后重试");
        }
        if (target == ContractStatus.TERMINATED && repository.countLiveLeases(id) > 0) {
            throw new BusinessException(409, "合同仍有关联的有效租约，请先通过租约退租或终止流程释放房间");
        }
        ContractStateMachine.requireTransition(current.status(), target);
        long nextVersion = repository.transition(id, current.status(), target, current.version(), operatorId);
        repository.addStatusHistory(
                id, current.status(), target, request.reason(), operatorId, nextVersion, null
        );
        ContractResponse updated = repository.find(id);
        record("Contract", id, target.name(), current, updated, operatorId, eventType);
        return updated;
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(400, "合同结束日期不能早于开始日期");
        }
    }

    private void record(
            String aggregateType,
            Long aggregateId,
            String action,
            Object before,
            Object after,
            Long operatorId,
            String eventType
    ) {
        String beforeJson = json(before);
        String afterJson = json(after);
        domainEvents.audit(aggregateType, aggregateId, action, beforeJson, afterJson, operatorId, null);
        domainEvents.outbox(aggregateType, aggregateId, eventType, afterJson);
    }

    private String json(Object value) {
        if (value == null) return "{}";
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "领域审计快照序列化失败");
        }
    }
}
