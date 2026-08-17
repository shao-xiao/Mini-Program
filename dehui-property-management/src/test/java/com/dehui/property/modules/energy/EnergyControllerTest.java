package com.dehui.property.modules.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.energy.controller.EnergyController;
import com.dehui.property.modules.customfield.application.CustomFieldImportService;
import com.dehui.property.modules.customfield.application.CustomFieldProjectionService;
import com.dehui.property.modules.customfield.application.CustomFieldQueryService;
import com.dehui.property.security.AuthPrincipal;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class EnergyControllerTest {

    private static final AuthPrincipal TECH = new AuthPrincipal(
            37L,
            "tech",
            "INTERNAL",
            null,
            List.of("ENGINEER"),
            List.of("energy:view", "energy:create", "energy:reading:update")
    );

    @Test
    void fullDependencyConstructorIsSelectedForRuntimeInjection() throws Exception {
        var constructor = EnergyController.class.getDeclaredConstructor(
                JdbcTemplate.class,
                CustomFieldQueryService.class,
                CustomFieldProjectionService.class,
                CustomFieldImportService.class
        );

        assertTrue(constructor.isAnnotationPresent(Autowired.class));
    }

    @Test
    void metersReturnsPagedMeterLedgerRows() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);

        ApiResponse<?> response = new EnergyController(jdbcTemplate).meters(
                "B1",
                "ELECTRIC",
                "ENABLED",
                null,
                null,
                null,
                null,
                1,
                20
        );

        assertEquals(200, response.code());
    }

    @Test
    void metersUseWhitelistedWorkspaceOrdering() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        new EnergyController(jdbcTemplate).meters(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "meterName:asc,unknown:desc",
                "meterType:desc",
                1,
                20
        );

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
        org.junit.jupiter.api.Assertions.assertTrue(sqlCaptor.getValue().contains("ORDER BY m.meter_type DESC, m.meter_name ASC, m.id DESC"));
        org.junit.jupiter.api.Assertions.assertTrue(!sqlCaptor.getValue().contains("unknown"));
    }

    @Test
    void propertySubjectFiltersMetersByUnassignedTenant() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        new EnergyController(jdbcTemplate).meters(
                null, null, null, null, null, null, null,
                "PROPERTY", null, null, null, 1, 20
        );

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
        assertTrue(sqlCaptor.getValue().contains("m.tenant_id IS NULL"));
    }

    @Test
    void subjectScopeRejectsUnknownValueAndTenantConflict() {
        EnergyController controller = new EnergyController(mock(JdbcTemplate.class));

        BusinessException unknown = assertThrows(BusinessException.class, () -> controller.meters(
                null, null, null, null, null, null, null,
                "UNKNOWN", null, null, null, 1, 20
        ));
        BusinessException conflict = assertThrows(BusinessException.class, () -> controller.meters(
                null, null, null, null, null, null, 8L,
                "PROPERTY", null, null, null, 1, 20
        ));

        assertEquals(400, unknown.getCode());
        assertEquals(400, conflict.getCode());
    }

    @Test
    void readingsUseWhitelistedWorkspaceOrdering() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        new EnergyController(jdbcTemplate).readings(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "readingDate:desc,settlementAmount:asc,unknown:asc",
                "billStatus:asc",
                1,
                20
        );

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
        org.junit.jupiter.api.Assertions.assertTrue(sqlCaptor.getValue().contains("ORDER BY er.bill_status ASC, er.reading_date DESC, er.settlement_amount ASC, er.id DESC"));
        org.junit.jupiter.api.Assertions.assertTrue(!sqlCaptor.getValue().contains("unknown"));
    }

    @Test
    void readingsExposeLinkedFinancialBillNumber() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        new EnergyController(jdbcTemplate).readings(
                null, null, null, null, null, null, null,
                "PROPERTY", null, null, null, null, null, 1, 20
        );

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
        assertTrue(sqlCaptor.getValue().contains("linked_bill.code AS billNumber"));
        assertTrue(sqlCaptor.getValue().contains("m.tenant_id IS NULL"));
    }

    @Test
    void createMeterWritesConfiguredMeter() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        ApiResponse<Void> response = new EnergyController(jdbcTemplate).createMeter(Map.ofEntries(
                Map.entry("meterNo", "EL-B1-001"),
                Map.entry("meterName", "B1总电表"),
                Map.entry("meterType", "ELECTRIC"),
                Map.entry("buildingId", 1),
                Map.entry("floorId", 2),
                Map.entry("roomId", 3),
                Map.entry("tenantId", 4),
                Map.entry("unit", "kWh"),
                Map.entry("multiplier", "1"),
                Map.entry("status", "ENABLED"),
                Map.entry("remark", "测试表具")
        ));

        assertEquals(200, response.code());
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(eq(EnergyController.INSERT_METER_SQL), args.capture());
        assertEquals("EL-B1-001", args.getValue()[1]);
        assertEquals("ELECTRIC", args.getValue()[3]);
        assertEquals(1L, args.getValue()[4]);
        assertEquals(2L, args.getValue()[5]);
        assertEquals(3L, args.getValue()[6]);
        assertEquals("kWh", args.getValue()[8]);
        assertEquals(new BigDecimal("1"), args.getValue()[9]);
        assertEquals("ENABLED", args.getValue()[12]);
        assertEquals("测试表具", args.getValue()[15]);
    }

    @Test
    void createTenantMeterAlsoCreatesEffectiveDatedSubjectBinding() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        ApiResponse<Void> response = new EnergyController(jdbcTemplate).createMeter(Map.ofEntries(
                Map.entry("meterNo", "EL-T17-001"),
                Map.entry("meterName", "租户17电表"),
                Map.entry("meterType", "ELECTRIC"),
                Map.entry("buildingId", 1),
                Map.entry("floorId", 2),
                Map.entry("roomId", 3),
                Map.entry("tenantId", 17),
                Map.entry("subjectType", "TENANT"),
                Map.entry("effectiveFrom", "2026-07-30")
        ));

        assertEquals(200, response.code());
        ArgumentCaptor<Object[]> bindingArgs = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("INSERT INTO energy_meter_binding"),
                bindingArgs.capture()
        );
        assertEquals(1L, bindingArgs.getValue()[1]);
        assertEquals("TENANT", bindingArgs.getValue()[2]);
        assertEquals(17L, bindingArgs.getValue()[3]);
        assertEquals(3L, bindingArgs.getValue()[4]);
        assertEquals(java.sql.Date.valueOf("2026-07-30"), bindingArgs.getValue()[5]);
    }

    @Test
    void meterEditBackdatesOnlyItsSoleOpenBindingToCommissioningDate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("FROM energy_meter_binding"),
                eq(55L)
        )).thenReturn(List.of(Map.of(
                "id", 77L,
                "effectiveFrom", java.sql.Date.valueOf("2026-07-30"),
                "status", "ACTIVE"
        )));

        ApiResponse<Void> response = new EnergyController(jdbcTemplate).updateMeter(55L, Map.of(
                "meterNo", "WATER-55",
                "meterType", "WATER",
                "commissioningDate", "2026-06-01"
        ));

        assertEquals(200, response.code());
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("UPDATE energy_meter_binding"),
                eq(java.sql.Date.valueOf("2026-06-01")),
                eq(77L),
                eq(java.sql.Date.valueOf("2026-06-01"))
        );
    }

    @Test
    void lastReadingUsesTheLatestReadingBeforeSelectedPeriod() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("period_month < ?"),
                eq("2026-07"),
                eq(55L)
        )).thenReturn(List.of(Map.of(
                "id", 55L,
                "meterNo", "WATER-55",
                "meterType", "WATER",
                "previousReading", new BigDecimal("20.00")
        )));

        ApiResponse<Map<String, Object>> response =
                new EnergyController(jdbcTemplate).lastReading(55L, "2026-07");

        assertEquals(200, response.code());
        assertEquals(new BigDecimal("20.00"), response.data().get("previousReading"));
    }

    @Test
    void backdatedReadingExtendsTheSoleOpenInitialBindingToItsAccountingPeriod() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("SELECT * FROM energy_meter"),
                eq(10L)
        )).thenReturn(List.of(Map.of("multiplier", BigDecimal.ONE)));
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("period_month < ?"),
                eq(10L),
                eq("2026-06")
        )).thenReturn(List.of());
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("FROM energy_meter_binding"),
                eq(10L)
        )).thenReturn(List.of(Map.of(
                "id", 81L,
                "effectiveFrom", java.sql.Date.valueOf("2026-07-30"),
                "status", "ACTIVE"
        )));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        ApiResponse<Void> response = new EnergyController(jdbcTemplate).createReading(Map.of(
                "meterId", 10L,
                "periodMonth", "2026-06",
                "readingDate", "2026-06-30",
                "currentReading", "20.00",
                "unitPrice", "1.0000"
        ), TECH);

        assertEquals(200, response.code());
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("UPDATE energy_meter_binding"),
                eq(java.sql.Date.valueOf("2026-06-01")),
                eq(81L),
                eq(java.sql.Date.valueOf("2026-06-01"))
        );
    }

    @Test
    void readingDateCannotPrecedeMeterCommissioningDate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("SELECT * FROM energy_meter"),
                eq(10L)
        )).thenReturn(List.of(Map.of(
                "multiplier", BigDecimal.ONE,
                "commissioning_date", java.sql.Date.valueOf("2026-06-15")
        )));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new EnergyController(jdbcTemplate).createReading(Map.of(
                        "meterId", 10L,
                        "periodMonth", "2026-06",
                        "readingDate", "2026-06-14",
                        "currentReading", "20.00",
                        "unitPrice", "1.0000"
                ), TECH)
        );

        assertEquals(400, exception.getCode());
        assertEquals("抄表日期不能早于表具投运日期 2026-06-15", exception.getMessage());
        verify(jdbcTemplate, never()).update(
                org.mockito.ArgumentMatchers.contains("INSERT INTO energy_reading"),
                any(Object[].class)
        );
    }

    @Test
    void firstReadingNeverBackdatesInitialBindingBeforeCommissioningDate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("SELECT * FROM energy_meter"),
                eq(10L)
        )).thenReturn(List.of(Map.of(
                "multiplier", BigDecimal.ONE,
                "commissioning_date", java.sql.Date.valueOf("2026-06-15")
        )));
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("period_month < ?"),
                eq(10L),
                eq("2026-06")
        )).thenReturn(List.of());
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("FROM energy_meter_binding"),
                eq(10L)
        )).thenReturn(List.of(Map.of(
                "id", 81L,
                "effectiveFrom", java.sql.Date.valueOf("2026-07-30"),
                "status", "ACTIVE"
        )));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        ApiResponse<Void> response = new EnergyController(jdbcTemplate).createReading(Map.of(
                "meterId", 10L,
                "periodMonth", "2026-06",
                "readingDate", "2026-06-30",
                "currentReading", "20.00",
                "unitPrice", "1.0000"
        ), TECH);

        assertEquals(200, response.code());
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("UPDATE energy_meter_binding"),
                eq(java.sql.Date.valueOf("2026-06-15")),
                eq(81L),
                eq(java.sql.Date.valueOf("2026-06-15"))
        );
    }

    @Test
    void monthlyReadingTasksGenerateMissingRecordsForEnabledMeters() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
                "id", 10L,
                "multiplier", new BigDecimal("1.0000")
        )));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(), any())).thenReturn(0L);
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("SELECT current_reading"),
                any(Long.class)
        )).thenReturn(List.of(Map.of("current_reading", new BigDecimal("12.00"))));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        ApiResponse<Map<String, Object>> response = new EnergyController(jdbcTemplate).generateMonthlyReadingTasks(Map.<String, Object>of(
                "periodMonth", "2026-07",
                "readingDate", "2026-07-25",
                "meterType", "ELECTRIC",
                "operatorName", "spoofed-admin"
        ), TECH);

        assertEquals(200, response.code());
        assertEquals(1, response.data().get("createdCount"));
        verify(jdbcTemplate).queryForList(
                org.mockito.ArgumentMatchers.contains("m.status = ?"),
                any(Object[].class)
        );
        ArgumentCaptor<Object[]> insertArgs = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("INSERT INTO energy_reading"),
                insertArgs.capture()
        );
        assertEquals(10L, insertArgs.getValue()[1]);
        assertEquals("2026-07", insertArgs.getValue()[2]);
        assertEquals("2026-07-25", insertArgs.getValue()[3]);
        assertEquals("tech", insertArgs.getValue()[10]);
        assertEquals("AUTO", insertArgs.getValue()[11]);
        assertEquals(37L, insertArgs.getValue()[16]);
        assertEquals(37L, insertArgs.getValue()[17]);
    }

    @Test
    void statsSeparatesTenantScopeFilterFromGroupByClause() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("GROUP BY m.meter_type")) {
                assertTrue(sql.matches("(?s).*m\\.tenant_id = \\?\\s+GROUP BY m\\.meter_type.*"));
            }
            return List.of();
        });

        ApiResponse<Map<String, Object>> response = new EnergyController(jdbcTemplate).stats(
                null,
                null,
                null,
                null,
                null,
                7L,
                null,
                null,
                null
        );

        assertEquals(200, response.code());
        assertEquals(0L, response.data().get("recordCount"));
    }

    @Test
    void monthlyReadingTasksUsePreviousReadingBeforeTargetPeriod() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
                "id", 10L,
                "multiplier", new BigDecimal("1.0000")
        )));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(), any())).thenReturn(0L);
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("period_month < ?"),
                any(Long.class),
                eq("2026-05")
        )).thenReturn(List.of(Map.of("current_reading", new BigDecimal("0.00"))));
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        ApiResponse<Map<String, Object>> response = new EnergyController(jdbcTemplate).generateMonthlyReadingTasks(Map.<String, Object>of(
                "periodMonth", "2026-05",
                "readingDate", "2026-05-26",
                "meterType", "ELECTRIC"
        ), TECH);

        assertEquals(200, response.code());
        verify(jdbcTemplate).queryForList(
                org.mockito.ArgumentMatchers.contains("period_month < ?"),
                any(Long.class),
                eq("2026-05")
        );
    }

    @Test
    void monthlyReadingTasksReadConfiguredPeriodPriceByMeterType() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                Map.of("id", 10L, "meterType", "ELECTRIC", "multiplier", new BigDecimal("1.0000")),
                Map.of("id", 11L, "meterType", "WATER", "multiplier", new BigDecimal("1.0000"))
        ));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(), any())).thenReturn(0L);
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("period_month < ?"),
                any(Long.class),
                eq("2026-07")
        )).thenReturn(List.of());
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("FROM energy_price_setting"),
                eq("2026-07"),
                eq("ELECTRIC")
        )).thenReturn(List.of(Map.of("unit_price", new BigDecimal("0.8800"))));
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("FROM energy_price_setting"),
                eq("2026-07"),
                eq("WATER")
        )).thenReturn(List.of(Map.of("unit_price", new BigDecimal("3.5000"))));
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        ApiResponse<Map<String, Object>> response = new EnergyController(jdbcTemplate).generateMonthlyReadingTasks(Map.<String, Object>of(
                "periodMonth", "2026-07",
                "readingDate", "2026-07-25"
        ), TECH);

        assertEquals(200, response.code());
        assertEquals(2, response.data().get("createdCount"));
        verify(jdbcTemplate).queryForList(
                org.mockito.ArgumentMatchers.contains("FROM energy_price_setting"),
                eq("2026-07"),
                eq("ELECTRIC")
        );
        verify(jdbcTemplate).queryForList(
                org.mockito.ArgumentMatchers.contains("FROM energy_price_setting"),
                eq("2026-07"),
                eq("WATER")
        );
    }

    @Test
    void savePriceSettingsUpsertsOnlySupportedMeterTypes() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any())).thenReturn(1);

        ApiResponse<Map<String, Object>> response = new EnergyController(jdbcTemplate).savePriceSettings(Map.<String, Object>of(
                "periodMonth", "2026-07",
                "electricPrice", "0.88",
                "waterPrice", "3.50",
                "gasPrice", "3.35"
        ));

        assertEquals(200, response.code());
        assertEquals(3, response.data().get("savedCount"));
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("INSERT INTO energy_price_setting"),
                any(),
                eq("2026-07"),
                eq("ELECTRIC"),
                eq(new BigDecimal("0.8800")),
                eq(0L),
                eq(0L)
        );
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("INSERT INTO energy_price_setting"),
                any(),
                eq("2026-07"),
                eq("WATER"),
                eq(new BigDecimal("3.5000")),
                eq(0L),
                eq(0L)
        );
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("INSERT INTO energy_price_setting"),
                any(),
                eq("2026-07"),
                eq("GAS"),
                eq(new BigDecimal("3.3500")),
                eq(0L),
                eq(0L)
        );
    }

    @Test
    void importReadingValuesUpdatesExistingTasksByMeterNo() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CustomFieldImportService customFieldImportService = mock(CustomFieldImportService.class);
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("m.meter_no = ?"),
                any(String.class)
        )).thenReturn(List.of(Map.of(
                "id", 10L,
                "multiplier", new BigDecimal("1.0000")
        )));
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("SELECT id FROM energy_reading"),
                any(Long.class),
                any(String.class)
        )).thenReturn(List.of(Map.of("id", 99L)));
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("period_month < ?"),
                any(Long.class),
                eq("2026-07")
        )).thenReturn(List.of(Map.of("current_reading", new BigDecimal("12.00"))));
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("SELECT * FROM energy_meter"),
                any(Long.class)
        )).thenReturn(List.of(Map.of("multiplier", new BigDecimal("1.0000"))));
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("attachment_file_id, reset_reason"),
                any(Long.class)
        )).thenReturn(List.of(Map.of("id", 99L, "period_month", "2026-07")));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("period_month > ?"),
                any(Long.class),
                eq("2026-07")
        )).thenReturn(List.of());

        ApiResponse<Map<String, Object>> response = new EnergyController(
                jdbcTemplate,
                customFieldImportService
        ).importReadingValues(Map.of(
                "periodMonth", "2026-07",
                "operatorName", "request-spoof",
                "readings", List.of(Map.of(
                        "meterNo", "BULK-TEST-E-100",
                        "currentReading", "18.50",
                        "unitPrice", "1.2",
                        "operatorName", "row-spoof"
                ))
        ), TECH);

        assertEquals(200, response.code());
        assertEquals(1, response.data().get("successCount"));
        assertEquals(1, response.data().get("updatedCount"));
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("SET meter_id = ?"),
                eq(10L),
                eq("2026-07"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq("tech"),
                eq("IMPORT"),
                isNull(),
                isNull(),
                eq(0),
                isNull(),
                eq(37L),
                eq("导入读数"),
                eq(99L)
        );
        verify(customFieldImportService).saveImportedRow(eq("energy-records"), eq(99L), any(), eq(Map.of()));
    }

    @Test
    void importReadingValuesRecalculatesLaterNotGeneratedReadings() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CustomFieldImportService customFieldImportService = mock(CustomFieldImportService.class);
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("m.meter_no = ?"),
                any(String.class)
        )).thenReturn(List.of(Map.of(
                "id", 10L,
                "multiplier", new BigDecimal("1.0000")
        )));
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("SELECT id FROM energy_reading"),
                any(Long.class),
                any(String.class)
        )).thenReturn(List.of(Map.of("id", 99L)));
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("period_month < ?"),
                any(Long.class),
                eq("2026-05")
        )).thenReturn(List.of(Map.of("current_reading", BigDecimal.ZERO)));
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("SELECT * FROM energy_meter"),
                any(Long.class)
        )).thenReturn(List.of(Map.of("multiplier", new BigDecimal("1.0000"))));
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("attachment_file_id, reset_reason"),
                any(Long.class)
        )).thenReturn(List.of(Map.of("id", 99L, "period_month", "2026-05")));
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("period_month > ?"),
                any(Long.class),
                eq("2026-05")
        )).thenReturn(List.of());
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        ApiResponse<Map<String, Object>> response = new EnergyController(
                jdbcTemplate,
                customFieldImportService
        ).importReadingValues(Map.of(
                "periodMonth", "2026-05",
                "readings", List.of(Map.of(
                        "meterNo", "BULK-TEST-E-100",
                        "currentReading", "18.50",
                        "unitPrice", "1.2"
                ))
        ), TECH);

        assertEquals(200, response.code());
        verify(jdbcTemplate).queryForList(
                org.mockito.ArgumentMatchers.contains("period_month > ?"),
                any(Long.class),
                eq("2026-05")
        );
    }

    @Test
    void batchGenerateBillsUsesReadingFilters() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("SELECT er.id"),
                any(Object[].class)
        )).thenReturn(List.of());

        ApiResponse<Map<String, Object>> response = new EnergyController(jdbcTemplate).batchGenerateBills(Map.<String, Object>of(
                "periodMonth", "2026-07",
                "meterType", "WATER"
        ), TECH);

        assertEquals(200, response.code());
        assertEquals(0, response.data().get("successCount"));
        verify(jdbcTemplate).queryForList(
                org.mockito.ArgumentMatchers.contains("er.bill_status = ?"),
                any(Object[].class)
        );
    }

    @Test
    void unauthenticatedReadingMutationIsRejected() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new EnergyController(jdbcTemplate).createReading(Map.of(), null)
        );

        assertEquals(401, exception.getCode());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void duplicateMeterPeriodReturnsConflictWithActionableMessage() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("SELECT * FROM energy_meter"),
                eq(10L)
        )).thenReturn(List.of(Map.of("multiplier", BigDecimal.ONE)));
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("period_month < ?"),
                eq(10L),
                eq("2026-07")
        )).thenReturn(List.of(Map.of("current_reading", new BigDecimal("1570.00"))));
        when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenThrow(new DuplicateKeyException("uk_energy_reading_meter_period"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new EnergyController(jdbcTemplate).createReading(Map.of(
                        "meterId", 10L,
                        "periodMonth", "2026-07",
                        "readingDate", "2026-07-22",
                        "currentReading", "10001.00",
                        "unitPrice", "1.0000"
                ), TECH)
        );

        assertEquals(409, exception.getCode());
        assertTrue(exception.getMessage().contains("2026-07"));
        assertTrue(exception.getMessage().contains("请勿重复新增"));
    }

    @Test
    void readingMutationRejectsInvalidPeriodMonthBeforeWriting() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new EnergyController(jdbcTemplate).createReading(Map.of(
                        "meterId", 10L,
                        "periodMonth", "2026-13",
                        "currentReading", "10.00",
                        "unitPrice", "1.0000"
                ), TECH)
        );

        assertEquals(400, exception.getCode());
        assertEquals("账期格式不正确，请使用 YYYY-MM", exception.getMessage());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void allEnergyPeriodMutationEntriesUseStrictYearMonthValidation() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EnergyController controller = new EnergyController(jdbcTemplate);

        BusinessException priceSettings = assertThrows(
                BusinessException.class,
                () -> controller.savePriceSettings(Map.of("periodMonth", "2026-00"))
        );
        BusinessException monthlyTasks = assertThrows(
                BusinessException.class,
                () -> controller.generateMonthlyReadingTasks(Map.of("periodMonth", "2026-00"), TECH)
        );
        BusinessException readingImport = assertThrows(
                BusinessException.class,
                () -> controller.importReadingValues(Map.of("periodMonth", "2026-00"), TECH)
        );

        assertEquals(400, priceSettings.getCode());
        assertEquals(400, monthlyTasks.getCode());
        assertEquals(400, readingImport.getCode());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void updateReadingDoesNotReportSuccessWhenNoEditableRecordWasUpdated() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("SELECT * FROM energy_meter"),
                eq(10L)
        )).thenReturn(List.of(Map.of("multiplier", BigDecimal.ONE)));
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("period_month < ?"),
                eq(10L),
                eq("2026-07")
        )).thenReturn(List.of());
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new EnergyController(jdbcTemplate).updateReading(88L, Map.of(
                        "meterId", 10L,
                        "periodMonth", "2026-07",
                        "readingDate", "2026-07-22",
                        "currentReading", "10.00",
                        "unitPrice", "1.0000",
                        "changeReason", "更正测试读数"
                ), TECH)
        );

        assertEquals(404, exception.getCode());
    }

    @Test
    void withdrawBillCancelsLinkedEnergyBillAndResetsReadingByIds() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("FROM energy_reading er"),
                any(Long.class)
        )).thenReturn(List.of(Map.of(
                "id", 77L,
                "bill_id", 900L,
                "bill_status", "GENERATED",
                "tenant_id", 8L,
                "meter_type", "ELECTRIC"
        )));
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("FROM bill b"),
                any(Long.class),
                any(Long.class)
        )).thenReturn(List.of(Map.of(
                "id", 900L,
                "payStatus", "UNPAID",
                "paidAmount", BigDecimal.ZERO
        )));
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("FROM bill_invoice i"),
                any(Long.class)
        )).thenReturn(List.of());
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        ApiResponse<Void> response = new EnergyController(jdbcTemplate).withdrawBill(77L, Map.of(
                "reason", "wrong reading",
                "operatorName", "spoofed-admin"
        ), TECH);

        assertEquals(200, response.code());
        verify(jdbcTemplate).queryForList(
                org.mockito.ArgumentMatchers.contains("b.source_type = 'ENERGY' AND b.source_id = ?"),
                eq(900L),
                eq(77L)
        );
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("pay_status = 'CANCELLED'"),
                eq(37L),
                eq(900L)
        );
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("bill_status = 'NOT_GENERATED'"),
                eq(37L),
                eq(77L)
        );
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("INSERT INTO energy_bill_operation_log"),
                any(),
                eq(77L),
                eq(900L),
                eq("WITHDRAW_BILL"),
                eq("wrong reading"),
                eq("SUCCESS"),
                any(),
                eq(37L),
                eq("tech")
        );
    }

    @Test
    void withdrawBillRejectsPaidLinkedBill() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("FROM energy_reading er"),
                any(Long.class)
        )).thenReturn(List.of(Map.of(
                "id", 77L,
                "bill_id", 900L,
                "bill_status", "GENERATED",
                "tenant_id", 8L,
                "meter_type", "ELECTRIC"
        )));
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("FROM bill b"),
                any(Long.class),
                any(Long.class)
        )).thenReturn(List.of(Map.of(
                "id", 900L,
                "payStatus", "PAID",
                "paidAmount", new BigDecimal("12.00")
        )));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new EnergyController(jdbcTemplate).withdrawBill(77L, Map.of(), TECH)
        );

        assertEquals(400, exception.getCode());
        assertEquals("该账单已收款，不能撤回", exception.getMessage());
    }

    @Test
    void withdrawBillRejectsInvoicedLinkedBill() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("FROM energy_reading er"),
                any(Long.class)
        )).thenReturn(List.of(Map.of(
                "id", 77L,
                "bill_id", 900L,
                "bill_status", "GENERATED",
                "tenant_id", 8L,
                "meter_type", "ELECTRIC"
        )));
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("FROM bill b"),
                any(Long.class),
                any(Long.class)
        )).thenReturn(List.of(Map.of(
                "id", 900L,
                "payStatus", "UNPAID",
                "paidAmount", BigDecimal.ZERO
        )));
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("FROM bill_invoice i"),
                any(Long.class)
        )).thenReturn(List.of(Map.of(
                "id", 15L,
                "invoiceStatus", "INVOICED",
                "hasInvoiceFile", 0
        )));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new EnergyController(jdbcTemplate).withdrawBill(77L, Map.of(), TECH)
        );

        assertEquals(400, exception.getCode());
        assertEquals("该账单已开票，不能撤回", exception.getMessage());
    }

    @Test
    void withdrawBillIsIdempotentWhenReadingIsAlreadyNotGenerated() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("FROM energy_reading er"),
                any(Long.class)
        )).thenReturn(List.of(Map.of(
                "id", 77L,
                "bill_status", "NOT_GENERATED",
                "tenant_id", 8L,
                "meter_type", "ELECTRIC"
        )));

        ApiResponse<Void> response = new EnergyController(jdbcTemplate).withdrawBill(77L, Map.of(), TECH);

        assertEquals(200, response.code());
        verify(jdbcTemplate, never()).update(
                org.mockito.ArgumentMatchers.contains("pay_status = 'CANCELLED'"),
                any(),
                any()
        );
    }

    @Test
    void withdrawBillRejectsGeneratedReadingWithoutLinkedBillId() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("FROM energy_reading er"),
                any(Long.class)
        )).thenReturn(List.of(Map.of(
                "id", 77L,
                "bill_status", "GENERATED",
                "tenant_id", 8L,
                "meter_type", "ELECTRIC"
        )));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new EnergyController(jdbcTemplate).withdrawBill(77L, Map.of(), TECH)
        );

        assertEquals(400, exception.getCode());
        assertEquals("未找到关联财务账单，请检查数据", exception.getMessage());
    }

    @Test
    void readingTemplateDownloadReturnsCsvAttachment() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CustomFieldImportService customFieldImportService = mock(CustomFieldImportService.class);
        com.dehui.property.modules.customfield.application.CustomFieldProjectionService projectionService =
                mock(com.dehui.property.modules.customfield.application.CustomFieldProjectionService.class);
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("SELECT er.id"),
                any(Object[].class)
        )).thenReturn(List.of(Map.ofEntries(
                Map.entry("meterNo", "BULK-TEST-E-097"),
                Map.entry("meterName", "测试电表097"),
                Map.entry("meterType", "ELECTRIC"),
                Map.entry("periodMonth", "2026-06"),
                Map.entry("readingDate", "2026-06-26"),
                Map.entry("previousReading", BigDecimal.ZERO),
                Map.entry("unitPrice", BigDecimal.ONE),
                Map.entry("roomName", "101"),
                Map.entry("tenantName", "-")
        )));

        when(customFieldImportService.enabledHeaders("energy-records")).thenReturn(List.of(
                new CustomFieldImportService.ImportFieldHeader(
                        "custom_review_level",
                        "复核等级",
                        com.dehui.property.modules.customfield.domain.CustomFieldType.TEXT,
                        Map.of()
                )
        ));
        when(projectionService.attach(eq("energy-records"), any(List.class))).thenAnswer(invocation -> {
            List<Map<String, Object>> source = invocation.getArgument(1);
            Map<String, Object> row = new java.util.LinkedHashMap<>(source.getFirst());
            row.put("customFields", Map.of("custom_review_level", "重点"));
            return List.of(row);
        });

        ResponseEntity<byte[]> response = new EnergyController(
                jdbcTemplate,
                null,
                projectionService,
                customFieldImportService
        ).downloadReadingTemplate(
                "ELECTRIC",
                "2026-06",
                "BULK-TEST",
                null,
                null,
                null,
                null,
                "NOT_GENERATED",
                false
        );

        assertEquals(200, response.getStatusCode().value());
        assertEquals("attachment", response.getHeaders().getContentDisposition().getType());
        String csv = new String(response.getBody(), StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("表号"));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("BULK-TEST-E-097"));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("本次读数"));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("复核等级"));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("重点"));
    }
}
