package com.dehui.property.modules.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.contract.domain.model.ContractStatus;
import com.dehui.property.modules.contract.infrastructure.repository.ContractRepository;
import com.dehui.property.modules.lease.application.LeaseApplicationService;
import com.dehui.property.modules.lease.domain.model.LeaseStatus;
import com.dehui.property.modules.lease.dto.LeaseCreateRequest;
import com.dehui.property.modules.lease.dto.LeaseResponse;
import com.dehui.property.modules.lease.infrastructure.repository.LeaseRepository;
import com.dehui.property.modules.lease.infrastructure.repository.LeaseRepository.ContractLeaseSnapshot;
import com.dehui.property.modules.shared.infrastructure.DomainEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class LeaseCreationTest {

    @Test
    void createReturnsPersistedLeaseForActiveContractTenant() {
        LeaseRepository repository = mock(LeaseRepository.class);
        ContractLeaseSnapshot contract = contract();
        LeaseCreateRequest request = request();
        LeaseResponse persisted = response();
        when(repository.lockContract(3L)).thenReturn(contract);
        when(repository.contractRoomIds(3L)).thenReturn(List.of(11L));
        when(repository.create(eq(request), eq(contract), eq(List.of(11L)), eq(null), eq(7001L))).thenReturn(55L);
        when(repository.find(55L)).thenReturn(persisted);
        when(repository.addEvent(eq(55L), eq("LeaseCreated"), any(String.class), eq(7001L))).thenReturn("event-55");
        LeaseApplicationService service = service(repository);

        LeaseResponse created = service.create(request, 7001L);

        assertEquals(55L, created.id());
        verify(repository).ensureTenantAvailable(8L);
        verify(repository).create(request, contract, List.of(11L), null, 7001L);
    }

    @Test
    void createRejectsDeletedOrDisabledContractTenantBeforeInsert() {
        LeaseRepository repository = mock(LeaseRepository.class);
        when(repository.lockContract(3L)).thenReturn(contract());
        doThrow(new BusinessException(400, "合同关联租户不存在或已停用"))
                .when(repository).ensureTenantAvailable(8L);
        LeaseApplicationService service = service(repository);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.create(request(), 7001L)
        );

        assertEquals(400, exception.getCode());
        verify(repository, never()).create(any(), any(), any(), any(), any());
    }

    private LeaseApplicationService service(LeaseRepository repository) {
        return new LeaseApplicationService(
                repository,
                mock(ContractRepository.class),
                mock(DomainEventRepository.class),
                new ObjectMapper().findAndRegisterModules()
        );
    }

    private ContractLeaseSnapshot contract() {
        return new ContractLeaseSnapshot(
                3L,
                "DEMO-CON-2026-001",
                8L,
                ContractStatus.ACTIVE,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                1,
                0L,
                31L
        );
    }

    private LeaseCreateRequest request() {
        return new LeaseCreateRequest(
                "LEASE-TEST-001",
                3L,
                8L,
                List.of(11L),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                "integration blocker regression"
        );
    }

    private LeaseResponse response() {
        return new LeaseResponse(
                55L,
                "LEASE-TEST-001",
                8L,
                "测试租户",
                3L,
                "DEMO-CON-2026-001",
                LeaseStatus.PENDING_CHECKIN,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                null,
                null,
                null,
                null,
                0L,
                1,
                0,
                0,
                List.of(11L),
                List.of("A-101"),
                List.of(),
                LocalDateTime.of(2026, 7, 21, 10, 0),
                LocalDateTime.of(2026, 7, 21, 10, 0)
        );
    }
}
