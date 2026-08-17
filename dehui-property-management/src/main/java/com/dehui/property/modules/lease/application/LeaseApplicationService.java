package com.dehui.property.modules.lease.application;

import com.dehui.property.common.BusinessException;
import com.dehui.property.common.PageResponse;
import com.dehui.property.modules.contract.domain.model.ContractStatus;
import com.dehui.property.modules.contract.domain.service.ContractStateMachine;
import com.dehui.property.modules.contract.dto.ContractResponse;
import com.dehui.property.modules.contract.infrastructure.repository.ContractRepository;
import com.dehui.property.modules.contract.infrastructure.repository.ContractRepository.ContractSnapshot;
import com.dehui.property.modules.lease.domain.model.LeaseRoomStatus;
import com.dehui.property.modules.lease.domain.model.LeaseStatus;
import com.dehui.property.modules.lease.domain.service.LeaseStatusDeriver;
import com.dehui.property.modules.lease.dto.LeaseActionRequest;
import com.dehui.property.modules.lease.dto.LeaseCreateRequest;
import com.dehui.property.modules.lease.dto.LeaseRenewRequest;
import com.dehui.property.modules.lease.dto.LeaseResponse;
import com.dehui.property.modules.lease.dto.LeaseSummaryResponse;
import com.dehui.property.modules.lease.infrastructure.repository.LeaseRepository;
import com.dehui.property.modules.lease.infrastructure.repository.LeaseRepository.ContractLeaseSnapshot;
import com.dehui.property.modules.lease.infrastructure.repository.LeaseRepository.LeaseCounts;
import com.dehui.property.modules.lease.infrastructure.repository.LeaseRepository.LeaseRoomSnapshot;
import com.dehui.property.modules.lease.infrastructure.repository.LeaseRepository.LeaseSnapshot;
import com.dehui.property.modules.shared.infrastructure.DomainEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeaseApplicationService {

    private final LeaseRepository repository;
    private final ContractRepository contractRepository;
    private final DomainEventRepository domainEvents;
    private final ObjectMapper objectMapper;

    public LeaseApplicationService(
            LeaseRepository repository,
            ContractRepository contractRepository,
            DomainEventRepository domainEvents,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.contractRepository = contractRepository;
        this.domainEvents = domainEvents;
        this.objectMapper = objectMapper;
    }

    public PageResponse<LeaseResponse> search(
            String keyword,
            String status,
            Long tenantId,
            Long contractId,
            String sortField,
            String sortDirection,
            Integer page,
            Integer pageSize
    ) {
        return search(keyword, status, tenantId, contractId, sortField, sortDirection, null, null, null, page, pageSize);
    }

    public PageResponse<LeaseResponse> search(
            String keyword,
            String status,
            Long tenantId,
            Long contractId,
            String sortField,
            String sortDirection,
            String sorts,
            String groupBy,
            String customFilters,
            Integer page,
            Integer pageSize
    ) {
        return repository.search(
                keyword, status, tenantId, contractId,
                sortField, sortDirection, sorts, groupBy, customFilters, page, pageSize
        );
    }

    public PageResponse<LeaseResponse> search(
            String keyword,
            String status,
            Long tenantId,
            Long contractId,
            String sortField,
            String sortDirection,
            String sorts,
            String groupBy,
            Integer page,
            Integer pageSize
    ) {
        return search(
                keyword, status, tenantId, contractId, sortField, sortDirection,
                sorts, groupBy, null, page, pageSize
        );
    }

    public LeaseSummaryResponse summary(String keyword, String status, Long tenantId, Long contractId) {
        return repository.summary(keyword, status, tenantId, contractId);
    }

    public LeaseSummaryResponse summary(
            String keyword,
            String status,
            Long tenantId,
            Long contractId,
            String customFilters
    ) {
        return repository.summary(keyword, status, tenantId, contractId, customFilters);
    }

    public LeaseResponse find(Long id) {
        return repository.find(id);
    }

    @Transactional
    public LeaseResponse create(LeaseCreateRequest request, Long operatorId) {
        validateDates(request.plannedStartDate(), request.plannedEndDate());
        ContractLeaseSnapshot contract = repository.lockContract(request.contractId());
        repository.ensureTenantAvailable(contract.tenantId());
        if (request.tenantId() != null && !request.tenantId().equals(contract.tenantId())) {
            throw new BusinessException(400, "租约租户必须与合同租户一致");
        }
        requireContractAllowsLease(contract.status());
        if (request.plannedStartDate().isBefore(contract.startDate())
                || request.plannedEndDate().isAfter(contract.endDate())) {
            throw new BusinessException(400, "租约计划区间必须位于合同有效期内");
        }
        List<Long> roomIds = resolveContractRooms(contract.id(), request.roomIds());
        Long leaseId = repository.create(request, contract, roomIds, null, operatorId);
        repository.addStatusHistory(
                leaseId, null, LeaseStatus.PENDING_CHECKIN, "租约创建", operatorId, 0, null
        );
        LeaseResponse created = repository.find(leaseId);
        recordLease(leaseId, "CREATE", null, created, operatorId, "LeaseCreated",
                Map.of("leaseId", leaseId, "roomIds", roomIds));
        return created;
    }

    @Transactional
    public LeaseResponse checkIn(Long leaseId, LeaseActionRequest rawRequest, Long operatorId) {
        LeaseActionRequest request = rawRequest == null ? LeaseActionRequest.empty() : rawRequest;
        LeaseSnapshot lease = repository.lockLease(leaseId);
        requireVersion(request.version(), lease.version());
        if (!Set.of(
                LeaseStatus.PENDING_CHECKIN,
                LeaseStatus.PARTIALLY_ACTIVE,
                LeaseStatus.PARTIALLY_TERMINATED
        ).contains(lease.status())) {
            throw new BusinessException(409, "当前租约状态不允许办理入住");
        }
        ContractLeaseSnapshot contract = repository.lockContract(lease.contractId());
        if (contract.status() != ContractStatus.ACTIVE) {
            throw new BusinessException(409, "合同必须先由有审批权限的人员审批生效，才能办理入住");
        }

        List<LeaseRoomSnapshot> allRooms = repository.lockLeaseRooms(leaseId);
        List<LeaseRoomSnapshot> targets = selectRooms(allRooms, request.roomIds(), LeaseRoomStatus.PENDING_CHECKIN);
        List<Long> targetRoomIds = targets.stream().map(LeaseRoomSnapshot::roomId).toList();
        repository.lockPhysicalRooms(targetRoomIds);
        repository.ensureRoomsAvailableForCheckIn(targetRoomIds);

        LocalDateTime occurredAt = request.occurredAt() == null ? LocalDateTime.now() : request.occurredAt();
        LocalDateTime occupancyEnd = lease.plannedEndDate().atTime(LocalTime.MAX);
        if (occurredAt.isAfter(occupancyEnd)) {
            throw new BusinessException(400, "入住时间不能晚于计划结束日期");
        }
        repository.ensureNoOverlappingOccupancy(leaseId, targetRoomIds, occurredAt, occupancyEnd);
        for (LeaseRoomSnapshot room : targets) {
            repository.checkInRoom(room, occurredAt, operatorId);
        }

        LeaseCounts counts = repository.counts(leaseId);
        LeaseStatus newStatus = LeaseStatusDeriver.derive(counts.total(), counts.active(), counts.terminated());
        long nextVersion = repository.updateLeaseStatus(
                lease, newStatus, occurredAt, null, null, operatorId
        );
        if (newStatus != lease.status()) {
            repository.addStatusHistory(
                    leaseId, lease.status(), newStatus, request.reason(), operatorId, nextVersion, null
            );
        }

        LeaseResponse updated = repository.find(leaseId);
        recordLease(leaseId, "CHECK_IN", lease, updated, operatorId, "LeaseRoomCheckedIn",
                Map.of("leaseId", leaseId, "roomIds", targetRoomIds, "occurredAt", occurredAt.toString()));
        return updated;
    }

    @Transactional
    public LeaseResponse checkInByContract(Long contractId, LeaseActionRequest rawRequest, Long operatorId) {
        LeaseActionRequest request = rawRequest == null ? LeaseActionRequest.empty() : rawRequest;
        Long leaseId = repository.uniqueActionableLeaseIdForContract(contractId);
        if (leaseId == null) {
            leaseId = createCompatibilityLeaseForContract(contractId, request.reason(), operatorId);
        }
        return checkIn(leaseId, request, operatorId);
    }

    private Long createCompatibilityLeaseForContract(Long contractId, String reason, Long operatorId) {
        ContractLeaseSnapshot contract = repository.lockContract(contractId);
        requireContractAllowsLease(contract.status());

        // Recheck after the contract row lock: two legacy requests must not create
        // competing Lease aggregates for the same historical contract.
        Long existingLeaseId = repository.uniqueActionableLeaseIdForContract(contractId);
        if (existingLeaseId != null) {
            return existingLeaseId;
        }

        LeaseCreateRequest compatibilityRequest = new LeaseCreateRequest(
                null,
                contractId,
                contract.tenantId(),
                repository.contractRoomIds(contractId),
                contract.startDate(),
                contract.endDate(),
                reason == null || reason.isBlank() ? "兼容合同入住入口自动建立租约" : reason
        );
        return create(compatibilityRequest, operatorId).id();
    }

    @Transactional
    public LeaseResponse legacyCheckInByRoom(
            Long roomId,
            Long contractId,
            String leaseNo,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            String reason,
            Long operatorId
    ) {
        Long leaseId = repository.uniqueLeaseIdForRoom(
                roomId, contractId, LeaseRoomStatus.PENDING_CHECKIN
        );
        if (leaseId == null) {
            if (contractId == null) {
                throw new BusinessException(409, "房间没有待入住租约；请提供明确的 contractId 创建租约");
            }
            ContractLeaseSnapshot contract = repository.lockContract(contractId);
            if (!repository.contractRoomIds(contractId).contains(roomId)) {
                throw new BusinessException(400, "目标房间不属于指定合同");
            }
            LeaseCreateRequest createRequest = new LeaseCreateRequest(
                    leaseNo,
                    contractId,
                    contract.tenantId(),
                    List.of(roomId),
                    plannedStartDate == null ? contract.startDate() : plannedStartDate,
                    plannedEndDate == null ? contract.endDate() : plannedEndDate,
                    reason
            );
            leaseId = create(createRequest, operatorId).id();
        }
        return checkIn(
                leaseId,
                new LeaseActionRequest(List.of(roomId), null, reason, null),
                operatorId
        );
    }

    @Transactional
    public LeaseResponse legacyCheckOutByRoom(Long roomId, String reason, Long operatorId) {
        Long leaseId = repository.uniqueLeaseIdForRoom(roomId, null, LeaseRoomStatus.ACTIVE);
        if (leaseId == null) {
            throw new BusinessException(409, "房间没有唯一的有效租约占用，禁止直接释放房态");
        }
        return checkOut(
                leaseId,
                new LeaseActionRequest(List.of(roomId), null, reason, null),
                operatorId
        );
    }

    @Transactional
    public LeaseResponse checkOut(Long leaseId, LeaseActionRequest rawRequest, Long operatorId) {
        LeaseActionRequest request = rawRequest == null ? LeaseActionRequest.empty() : rawRequest;
        LeaseSnapshot lease = repository.lockLease(leaseId);
        requireVersion(request.version(), lease.version());
        if (!Set.of(
                LeaseStatus.PARTIALLY_ACTIVE,
                LeaseStatus.ACTIVE,
                LeaseStatus.RENEWING,
                LeaseStatus.PARTIALLY_TERMINATED,
                LeaseStatus.CHECKOUT_PENDING
        ).contains(lease.status())) {
            throw new BusinessException(409, "当前租约状态不允许办理退租");
        }

        List<LeaseRoomSnapshot> allRooms = repository.lockLeaseRooms(leaseId);
        List<LeaseRoomSnapshot> targets = selectRooms(allRooms, request.roomIds(), LeaseRoomStatus.ACTIVE);
        List<Long> targetRoomIds = targets.stream().map(LeaseRoomSnapshot::roomId).toList();
        repository.lockPhysicalRooms(targetRoomIds);
        LocalDateTime occurredAt = request.occurredAt() == null ? LocalDateTime.now() : request.occurredAt();
        for (LeaseRoomSnapshot room : targets) {
            if (room.occupancyStartAt() != null && occurredAt.isBefore(room.occupancyStartAt())) {
                throw new BusinessException(400, "退租时间不能早于入住时间");
            }
            repository.checkOutRoom(room, occurredAt, operatorId);
        }

        LeaseCounts counts = repository.counts(leaseId);
        LeaseStatus newStatus = LeaseStatusDeriver.derive(counts.total(), counts.active(), counts.terminated());
        LocalDateTime actualCheckoutAt = newStatus == LeaseStatus.TERMINATED ? occurredAt : null;
        long nextVersion = repository.updateLeaseStatus(
                lease, newStatus, null, actualCheckoutAt, request.reason(), operatorId
        );
        if (newStatus != lease.status()) {
            repository.addStatusHistory(
                    leaseId, lease.status(), newStatus, request.reason(), operatorId, nextVersion, null
            );
        }
        if (newStatus == LeaseStatus.TERMINATED) {
            terminateContractWhenNoLiveLease(lease, request.reason(), operatorId);
        }

        LeaseResponse updated = repository.find(leaseId);
        recordLease(leaseId, "CHECK_OUT", lease, updated, operatorId, "LeaseRoomCheckedOut",
                Map.of("leaseId", leaseId, "roomIds", targetRoomIds, "occurredAt", occurredAt.toString()));
        return updated;
    }

    @Transactional
    public LeaseResponse terminate(Long leaseId, LeaseActionRequest rawRequest, Long operatorId) {
        LeaseActionRequest request = rawRequest == null ? LeaseActionRequest.empty() : rawRequest;
        LeaseSnapshot lease = repository.lockLease(leaseId);
        requireVersion(request.version(), lease.version());
        if (Set.of(LeaseStatus.TERMINATED, LeaseStatus.CANCELLED, LeaseStatus.EXPIRED).contains(lease.status())) {
            throw new BusinessException(409, "当前租约已处于终态");
        }
        List<LeaseRoomSnapshot> rooms = repository.lockLeaseRooms(leaseId);
        List<Long> physicalRoomIds = rooms.stream()
                .filter(room -> room.status() == LeaseRoomStatus.ACTIVE)
                .map(LeaseRoomSnapshot::roomId)
                .toList();
        repository.lockPhysicalRooms(physicalRoomIds);
        LocalDateTime occurredAt = request.occurredAt() == null ? LocalDateTime.now() : request.occurredAt();
        for (LeaseRoomSnapshot room : rooms) {
            if (room.status() == LeaseRoomStatus.ACTIVE) {
                repository.checkOutRoom(room, occurredAt, operatorId);
            } else if (room.status() == LeaseRoomStatus.PENDING_CHECKIN) {
                repository.cancelPendingRoom(room, operatorId);
            }
        }
        long nextVersion = repository.updateLeaseStatus(
                lease, LeaseStatus.TERMINATED, null, occurredAt, request.reason(), operatorId
        );
        repository.addStatusHistory(
                leaseId, lease.status(), LeaseStatus.TERMINATED, request.reason(), operatorId, nextVersion, null
        );
        terminateContractWhenNoLiveLease(lease, request.reason(), operatorId);
        LeaseResponse updated = repository.find(leaseId);
        recordLease(leaseId, "TERMINATE", lease, updated, operatorId, "LeaseTerminated",
                Map.of("leaseId", leaseId, "roomIds", physicalRoomIds, "occurredAt", occurredAt.toString()));
        return updated;
    }

    @Transactional
    public LeaseResponse renew(Long leaseId, LeaseRenewRequest request, Long operatorId) {
        validateDates(request.plannedStartDate(), request.plannedEndDate());
        LeaseSnapshot oldLease = repository.lockLease(leaseId);
        requireVersion(request.version(), oldLease.version());
        if (!Set.of(
                LeaseStatus.ACTIVE,
                LeaseStatus.PARTIALLY_ACTIVE,
                LeaseStatus.PARTIALLY_TERMINATED
        ).contains(oldLease.status())) {
            throw new BusinessException(409, "当前租约状态不允许续租");
        }
        if (!request.plannedStartDate().isAfter(oldLease.plannedEndDate())) {
            throw new BusinessException(400, "续租开始日期必须晚于原租约计划结束日期");
        }

        ContractSnapshot contract = contractRepository.lock(oldLease.contractId());
        if (contract.status() != ContractStatus.ACTIVE) {
            throw new BusinessException(409, "终态合同不能原地续租；请创建关联的新合同后再创建租约");
        }
        contractRepository.extendForRenewal(
                contract, request.plannedStartDate(), request.plannedEndDate(), request.reason(), operatorId
        );
        ContractLeaseSnapshot renewedContract = repository.lockContract(oldLease.contractId());
        List<Long> roomIds = repository.leaseRoomIds(oldLease.id());
        if (roomIds.isEmpty()) throw new BusinessException(409, "合同没有可续租房间");
        LeaseCreateRequest renewal = new LeaseCreateRequest(
                null,
                oldLease.contractId(),
                oldLease.tenantId(),
                roomIds,
                request.plannedStartDate(),
                request.plannedEndDate(),
                request.reason()
        );
        Long renewedLeaseId = repository.create(renewal, renewedContract, roomIds, oldLease.id(), operatorId);
        repository.addStatusHistory(
                renewedLeaseId, null, LeaseStatus.PENDING_CHECKIN, "续租创建", operatorId, 0, null
        );
        if (oldLease.status() != LeaseStatus.TERMINATED) {
            repository.markRenewing(oldLease, operatorId);
            repository.addStatusHistory(
                    oldLease.id(), oldLease.status(), LeaseStatus.RENEWING, request.reason(),
                    operatorId, oldLease.version() + 1, null
            );
        }
        LeaseResponse renewed = repository.find(renewedLeaseId);
        recordLease(renewedLeaseId, "RENEW", oldLease, renewed, operatorId, "LeaseRenewed",
                Map.of("leaseId", renewedLeaseId, "renewalOfLeaseId", oldLease.id(), "roomIds", roomIds));
        return renewed;
    }

    private void terminateContractWhenNoLiveLease(LeaseSnapshot lease, String reason, Long operatorId) {
        if (repository.countOtherLiveLeases(lease.contractId(), lease.id()) > 0) return;
        ContractSnapshot contract = contractRepository.lock(lease.contractId());
        if (!Set.of(ContractStatus.ACTIVE, ContractStatus.SUSPENDED).contains(contract.status())) return;
        ContractStateMachine.requireTransition(contract.status(), ContractStatus.TERMINATED);
        long nextVersion = contractRepository.transition(
                contract.id(), contract.status(), ContractStatus.TERMINATED, contract.version(), operatorId
        );
        contractRepository.addStatusHistory(
                contract.id(), contract.status(), ContractStatus.TERMINATED,
                reason == null ? "租约全部退租" : reason, operatorId, nextVersion, null
        );
        ContractResponse updated = contractRepository.find(contract.id());
        String payload = json(updated);
        domainEvents.audit("Contract", contract.id(), "TERMINATE_BY_CHECK_OUT", "{}", payload, operatorId, null);
        domainEvents.outbox("Contract", contract.id(), "ContractTerminated", payload);
    }

    private List<Long> resolveContractRooms(Long contractId, List<Long> requested) {
        List<Long> contractRoomIds = repository.contractRoomIds(contractId);
        if (contractRoomIds.isEmpty()) throw new BusinessException(409, "合同未关联房间");
        Set<Long> contractRooms = new LinkedHashSet<>(contractRoomIds);
        List<Long> selected = requested == null || requested.isEmpty()
                ? new ArrayList<>(contractRooms)
                : new ArrayList<>(new LinkedHashSet<>(requested));
        if (requested != null && selected.size() != requested.size()) {
            throw new BusinessException(400, "房间列表包含重复项");
        }
        if (selected.isEmpty() || !contractRooms.containsAll(selected)) {
            throw new BusinessException(400, "租约房间必须属于合同");
        }
        return selected;
    }

    private List<LeaseRoomSnapshot> selectRooms(
            List<LeaseRoomSnapshot> allRooms,
            List<Long> requested,
            LeaseRoomStatus expectedStatus
    ) {
        Set<Long> requestedIds = requested == null || requested.isEmpty()
                ? null
                : new LinkedHashSet<>(requested);
        if (requested != null && requestedIds != null && requestedIds.size() != requested.size()) {
            throw new BusinessException(400, "房间列表包含重复项");
        }
        Set<Long> allIds = allRooms.stream().map(LeaseRoomSnapshot::roomId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (requestedIds != null && !allIds.containsAll(requestedIds)) {
            throw new BusinessException(400, "目标房间不属于该租约");
        }
        List<LeaseRoomSnapshot> targets = allRooms.stream()
                .filter(room -> requestedIds == null
                        ? room.status() == expectedStatus
                        : requestedIds.contains(room.roomId()))
                .toList();
        if (targets.isEmpty()) throw new BusinessException(400, "没有可操作的租约房间");
        if (targets.stream().anyMatch(room -> room.status() != expectedStatus)) {
            throw new BusinessException(409, "目标房间当前状态不允许执行该操作");
        }
        return targets;
    }

    private void requireContractAllowsLease(ContractStatus status) {
        if (status != ContractStatus.ACTIVE) {
            throw new BusinessException(409, "仅已审批生效合同可以创建租约");
        }
    }

    private void requireVersion(Long requestedVersion, long currentVersion) {
        if (requestedVersion != null && requestedVersion != currentVersion) {
            throw new BusinessException(409, "租约版本已变化，请刷新后重试");
        }
    }

    private void validateDates(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) throw new BusinessException(400, "租约结束日期不能早于开始日期");
    }

    private void recordLease(
            Long leaseId,
            String action,
            Object before,
            Object after,
            Long operatorId,
            String eventType,
            Object eventPayload
    ) {
        String beforeJson = json(before);
        String afterJson = json(after);
        String eventJson = json(eventPayload);
        String eventId = repository.addEvent(leaseId, eventType, eventJson, operatorId);
        domainEvents.audit("Lease", leaseId, action, beforeJson, afterJson, operatorId, null);
        domainEvents.outbox(eventId, "Lease", leaseId, eventType, eventJson);
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
