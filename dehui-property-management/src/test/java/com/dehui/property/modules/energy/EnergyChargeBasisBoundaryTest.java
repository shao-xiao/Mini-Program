package com.dehui.property.modules.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.asset.application.AssetChargeBasisService;
import com.dehui.property.modules.energy.controller.EnergyController;
import com.dehui.property.security.AuthPrincipal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class EnergyChargeBasisBoundaryTest {

    private static final AuthPrincipal OPERATOR = new AuthPrincipal(
            37L, "tech", "STAFF", null, List.of("ENGINEER"), List.of("energy:reading:update")
    );

    @Test
    void confirmingReadingDelegatesToChargeBasisWithoutWritingBill() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AssetChargeBasisService chargeBasisService = mock(AssetChargeBasisService.class);
        when(chargeBasisService.confirmEnergyBasis(77L, 37L)).thenReturn(901L);

        ApiResponse<Void> response = controller(jdbcTemplate, chargeBasisService).generateBill(77L, OPERATOR);

        assertEquals(200, response.code());
        verify(chargeBasisService).confirmEnergyBasis(77L, 37L);
        verify(jdbcTemplate, never()).update(
                org.mockito.ArgumentMatchers.contains("INSERT INTO bill"),
                org.mockito.ArgumentMatchers.any(Object[].class)
        );
    }

    @Test
    void batchConfirmationReportsBasisValidationFailure() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AssetChargeBasisService chargeBasisService = mock(AssetChargeBasisService.class);
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("SELECT er.id"),
                org.mockito.ArgumentMatchers.any(Object[].class)
        )).thenReturn(List.of(Map.of("id", 77L)));
        when(chargeBasisService.confirmEnergyBasis(77L, 37L))
                .thenThrow(new BusinessException(409, "计量主体绑定缺失"));

        ApiResponse<Map<String, Object>> response = controller(jdbcTemplate, chargeBasisService)
                .batchGenerateBills(Map.of("periodMonth", "2026-07"), OPERATOR);

        assertEquals(0, response.data().get("successCount"));
        assertEquals(1, response.data().get("failureCount"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> failures = (List<Map<String, Object>>) response.data().get("failures");
        assertEquals("计量主体绑定缺失", failures.getFirst().get("message"));
    }

    private EnergyController controller(
            JdbcTemplate jdbcTemplate,
            AssetChargeBasisService chargeBasisService) {
        return new EnergyController(jdbcTemplate, null, null, null, chargeBasisService);
    }
}
