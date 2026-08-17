package com.dehui.property.modules.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.customfield.application.CustomFieldQueryCompiler.CompiledQuery;
import com.dehui.property.modules.customfield.application.CustomFieldQueryService;
import com.dehui.property.modules.parking.controller.ParkingController;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

class ParkingControllerTest {

    @Test
    void bindAllowsActivityParkingWithoutPlateNumber() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(1L, 0L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of(
                        "id", 17L,
                        "monthly_rate", new BigDecimal("650.00")
                )));

        new ParkingController(jdbcTemplate).bind(17L, Map.of(
                "partyId", 9L,
                "startDate", "2026-07-30",
                "billingType", "monthly",
                "monthlyFee", "650.00"
        ));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, times(2)).update(sqlCaptor.capture(), argsCaptor.capture());

        int insertIndex = sqlCaptor.getAllValues().stream()
                .map(String::trim)
                .toList()
                .indexOf(sqlCaptor.getAllValues().stream()
                        .map(String::trim)
                        .filter(sql -> sql.startsWith("INSERT INTO parking_assignment"))
                        .findFirst()
                        .orElseThrow());
        assertEquals("", argsCaptor.getAllValues().get(insertIndex)[5]);
    }

    @Test
    void assignmentsReturnParkingAndTenantSnapshotsWithoutExtraClientQueries() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());

        new ParkingController(jdbcTemplate).assignments();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sqlCaptor.capture());
        assertTrue(sqlCaptor.getValue().contains("ps.space_no AS spaceNo"));
        assertTrue(sqlCaptor.getValue().contains("t.name AS tenantName"));
        assertTrue(sqlCaptor.getValue().contains("pa.monthly_fee AS monthlyFee"));
    }

    @Test
    void createSpaceRequiresFloorLocationSelection() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        BusinessException exception = assertThrows(BusinessException.class, () -> new ParkingController(jdbcTemplate)
                .createSpace(Map.of(
                        "spaceNo", "B1-001",
                        "area", "A",
                        "type", "NORMAL"
                )));

        assertEquals(400, exception.getCode());
        assertEquals("Parking location is required", exception.getMessage());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void createSpacePersistsAreaSeparatelyFromLocation() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        new ParkingController(jdbcTemplate).createSpace(Map.of(
                "spaceNo", "A-B1-001",
                "area", "A",
                "floor", "B1",
                "type", "TRUCK",
                "remark", "truck space"
        ));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), argsCaptor.capture());
        String sql = sqlCaptor.getValue();
        Object[] args = argsCaptor.getValue();

        assertTrue(sql.contains("area"));
        assertTrue(sql.contains("location"));
        assertEquals("A-B1-001", args[1]);
        assertEquals("A", args[2]);
        assertEquals("B1", args[3]);
        assertEquals("TRUCK", args[4]);
    }

    @Test
    void spacesQuerySelectsAndFiltersAreaColumn() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);

        new ParkingController(jdbcTemplate).spaces(
                null, null, "A", null, null, null, null, null,
                null, null, null, null, null, null, null, 1, 20);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
        String sql = sqlCaptor.getValue();

        assertTrue(sql.contains("s.area AS area"));
        assertTrue(sql.contains("s.location AS floor"));
        assertTrue(sql.contains("a.monthly_fee AS monthlyFee"));
        assertTrue(sql.contains("s.area = ?"));
    }

    @Test
    void spacesQueryUsesNativeOrderingWhenCustomFieldPlanHasNoOrdering() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CustomFieldQueryService customFieldQueryService = mock(CustomFieldQueryService.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(customFieldQueryService.compile(eq("parking-spaces"), any(), any(), any(), any()))
                .thenReturn(new CompiledQuery("", List.of(), List.of(), List.of(), 0));

        new ParkingController(jdbcTemplate, customFieldQueryService, null).spaces(
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, 1, 20);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
        assertTrue(sqlCaptor.getValue().contains("ORDER BY s.space_no DESC, s.id DESC"));
    }

    @Test
    void spacesQuerySupportsWorkspaceFiltersOrderingAndSummary() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of(
                "totalCount", 0,
                "availableCount", 0,
                "occupiedCount", 0,
                "unavailableCount", 0
        ));

        ParkingController controller = new ParkingController(jdbcTemplate);
        controller.spaces(
                null, "OCCUPIED", "A", "B1", "B1", "MONTHLY", "德汇", "沪A",
                null, new BigDecimal("300"), new BigDecimal("800"), null, null,
                "area:asc,spaceNo:desc", "area:asc", 1, 20);
        controller.spaceSummary(
                null, "OCCUPIED", "A", "B1", "B1", "MONTHLY", "德汇", "沪A",
                null, new BigDecimal("300"), new BigDecimal("800"));

        ArgumentCaptor<String> listSqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(listSqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
        String listSql = listSqlCaptor.getValue();
        assertTrue(listSql.contains("s.space_no LIKE ?"));
        assertTrue(listSql.contains("t.name LIKE ?"));
        assertTrue(listSql.contains("a.car_plate_no LIKE ?"));
        assertTrue(listSql.contains("a.monthly_fee >= ?"));
        assertTrue(listSql.contains("a.monthly_fee <= ?"));
        assertTrue(listSql.contains("ORDER BY s.area ASC, s.space_no DESC"));

        ArgumentCaptor<String> summarySqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForMap(summarySqlCaptor.capture(), any(Object[].class));
        assertTrue(summarySqlCaptor.getValue().contains("COUNT(DISTINCT s.id) AS totalCount"));
        assertTrue(summarySqlCaptor.getValue().contains("COUNT(DISTINCT CASE WHEN s.rent_status = 'AVAILABLE'"));
    }

    @Test
    void customSpaceTypeOptionsCanBeCreatedAndListed() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(Map.of(
                "label", "货车车位",
                "value", "TRUCK"
        )));

        new ParkingController(jdbcTemplate).createSpaceType(Map.of("label", "货车车位"));
        var response = new ParkingController(jdbcTemplate).spaceTypes();

        verify(jdbcTemplate).update(
                anyString(),
                eq("TRUCK"),
                eq("货车车位"),
                eq(0L),
                eq(0L)
        );
        assertEquals("TRUCK", response.data().getFirst().get("value"));
    }

    @Test
    void createBillUsesTenantFromActiveAssignment() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        new ParkingController(jdbcTemplate).createBill(Map.of(
                "assignmentId", 9L,
                "amount", "680.50",
                "dueDate", "2026-07-31",
                "remark", "July parking fee"
        ));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), argsCaptor.capture());
        Object[] args = argsCaptor.getValue();

        assertTrue(sqlCaptor.getValue().contains("INSERT INTO parking_bill"));
        assertTrue(sqlCaptor.getValue().contains("SELECT ?, pa.id, pa.tenant_id"));
        assertEquals(new BigDecimal("680.50"), args[1]);
        assertEquals("2026-07", args[2]);
        assertEquals("2026-07-31", args[3]);
        assertEquals("July parking fee", args[6]);
        assertEquals(9L, args[7]);
    }

    @Test
    void createBillRejectsMissingActiveAssignment() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class, () -> new ParkingController(jdbcTemplate)
                .createBill(Map.of(
                        "assignmentId", 99L,
                        "amount", 500,
                        "dueDate", "2026-07-31"
                )));

        assertEquals(400, exception.getCode());
        assertEquals("Active parking assignment not found", exception.getMessage());
        verify(jdbcTemplate).update(anyString(), any(Object[].class));
    }

    @Test
    void billAssignmentsOnlyListsActiveBindingsWithDisplayFields() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());

        new ParkingController(jdbcTemplate).billAssignments();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sqlCaptor.capture());
        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("pa.id AS assignmentId"));
        assertTrue(sql.contains("ps.space_no AS spaceNo"));
        assertTrue(sql.contains("t.name AS tenantName"));
        assertTrue(sql.contains("pa.monthly_fee AS monthlyFee"));
        assertTrue(sql.contains("pa.status IN ('ACTIVE', 'ENABLED')"));
    }

    @Test
    void activeAssignmentMonthlyFeeSnapshotCannotBeOverwritten() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new ParkingController(jdbcTemplate)
                        .updateAssignmentMonthlyFee(9L, Map.of("monthlyFee", "680.00"))
        );

        assertEquals(409, exception.getCode());
        assertTrue(exception.getMessage().contains("snapshots cannot be overwritten"));
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }
}
