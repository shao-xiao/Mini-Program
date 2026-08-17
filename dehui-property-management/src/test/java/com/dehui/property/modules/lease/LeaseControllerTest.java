package com.dehui.property.modules.lease;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dehui.property.common.GlobalExceptionHandler;
import com.dehui.property.modules.lease.application.LeaseApplicationService;
import com.dehui.property.modules.lease.controller.LeaseController;
import com.dehui.property.modules.lease.domain.model.LeaseStatus;
import com.dehui.property.modules.lease.dto.LeaseCreateRequest;
import com.dehui.property.modules.lease.dto.LeaseResponse;
import com.dehui.property.security.AuthInterceptor;
import com.dehui.property.security.AuthPrincipal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LeaseControllerTest {

    private static final AuthPrincipal PRINCIPAL = new AuthPrincipal(
            7001L, "lease-tester", "SYSTEM", null, List.of("STAFF"), List.of("lease:create")
    );

    @Test
    void postLeasesReturnsCreatedRecord() throws Exception {
        LeaseApplicationService service = mock(LeaseApplicationService.class);
        when(service.create(any(LeaseCreateRequest.class), eq(7001L))).thenReturn(response());
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/leases")
                        .requestAttr(AuthInterceptor.PRINCIPAL_ATTRIBUTE, PRINCIPAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "contractId": 3,
                                  "tenantId": 8,
                                  "roomIds": [11],
                                  "plannedStartDate": "2026-07-01",
                                  "plannedEndDate": "2026-07-31"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(55));

        verify(service).create(any(LeaseCreateRequest.class), eq(7001L));
    }

    @Test
    void postLeasesRejectsMissingRequiredDates() throws Exception {
        LeaseApplicationService service = mock(LeaseApplicationService.class);

        mockMvc(service).perform(post("/leases")
                        .requestAttr(AuthInterceptor.PRINCIPAL_ATTRIBUTE, PRINCIPAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contractId\":3}"))
                .andExpect(status().isBadRequest());
    }

    private MockMvc mockMvc(LeaseApplicationService service) {
        return MockMvcBuilders.standaloneSetup(new LeaseController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private LeaseResponse response() {
        return new LeaseResponse(
                55L, "LEASE-TEST-001", 8L, "测试租户", 3L, "DEMO-CON-2026-001",
                LeaseStatus.PENDING_CHECKIN,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
                null, null, null, null, 0L, 1, 0, 0,
                List.of(11L), List.of("A-101"), List.of(),
                LocalDateTime.of(2026, 7, 21, 10, 0),
                LocalDateTime.of(2026, 7, 21, 10, 0)
        );
    }
}
