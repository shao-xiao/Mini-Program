package com.dehui.property.modules.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.energy.controller.EnergyMeterBindingController;
import com.dehui.property.security.AuthPrincipal;
import java.sql.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class EnergyMeterBindingControllerTest {

    private static final AuthPrincipal OPERATOR = new AuthPrincipal(
            37L,
            "energy-admin",
            "INTERNAL",
            null,
            List.of("ENGINEER"),
            List.of("energy:meter:update")
    );

    @Test
    void initialAttributionCannotStartBeforeMeterCommissioningDate() {
        JdbcTemplate jdbcTemplate = meterJdbcTemplate("2026-06-15");
        EnergyMeterBindingController controller = new EnergyMeterBindingController(jdbcTemplate);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                controller.createBinding(9L, Map.of(
                        "subjectType", "PROPERTY",
                        "effectiveFrom", "2026-06-14"
                ), OPERATOR)
        );

        assertEquals(400, exception.getCode());
        assertEquals("计量归属开始日期不能早于表具投运日期 2026-06-15", exception.getMessage());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void replacementDateMustBeLaterThanCurrentAttributionStart() {
        JdbcTemplate jdbcTemplate = meterJdbcTemplate("2026-06-01");
        when(jdbcTemplate.queryForList(contains("FROM energy_meter_binding"), eq(9L)))
                .thenReturn(List.of(Map.of(
                        "id", 81L,
                        "subjectType", "PROPERTY",
                        "effectiveFrom", Date.valueOf("2026-06-01")
                )));
        EnergyMeterBindingController controller = new EnergyMeterBindingController(jdbcTemplate);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                controller.replaceBinding(9L, Map.of(
                        "subjectType", "PUBLIC_AREA",
                        "effectiveFrom", "2026-06-01"
                ), OPERATOR)
        );

        assertEquals(409, exception.getCode());
        assertEquals(
                "新的计量归属开始日期必须晚于当前归属开始日期 2026-06-01",
                exception.getMessage()
        );
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void replacementRejectsUnchangedAttributionTarget() {
        JdbcTemplate jdbcTemplate = meterJdbcTemplate("2026-06-01");
        when(jdbcTemplate.queryForList(contains("FROM energy_meter_binding"), eq(9L)))
                .thenReturn(List.of(Map.of(
                        "id", 81L,
                        "subjectType", "PROPERTY",
                        "effectiveFrom", Date.valueOf("2026-06-01")
                )));
        EnergyMeterBindingController controller = new EnergyMeterBindingController(jdbcTemplate);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                controller.replaceBinding(9L, Map.of(
                        "subjectType", "PROPERTY",
                        "effectiveFrom", "2026-07-01"
                ), OPERATOR)
        );

        assertEquals(409, exception.getCode());
        assertEquals("新计量归属与当前归属相同，无需重复变更", exception.getMessage());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    private JdbcTemplate meterJdbcTemplate(String commissioningDate) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(contains("FROM energy_meter"), eq(9L)))
                .thenReturn(List.of(Map.of(
                        "id", 9L,
                        "commissioningDate", Date.valueOf(commissioningDate)
                )));
        return jdbcTemplate;
    }
}
