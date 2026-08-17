package com.dehui.property.modules.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.PageResponse;
import com.dehui.property.modules.building.controller.BuildingController;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class BuildingControllerPaginationTest {

    @Test
    void roomsReturnBackendPageShape() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(12L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(
                Map.of("id", 11L, "roomNumber", "1101"),
                Map.of("id", 12L, "roomNumber", "1102")
        ));

        ApiResponse<PageResponse<Map<String, Object>>> response = new BuildingController(jdbcTemplate)
                .rooms(1L, 2L, "VACANT", 2, 10);

        assertEquals(200, response.code());
        assertEquals(12L, response.data().total());
        assertEquals(2, response.data().page());
        assertEquals(10, response.data().pageSize());
        assertEquals(2, response.data().records().size());
        assertEquals("1101", response.data().records().getFirst().get("roomNumber"));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                sqlCaptor.capture(),
                any(RowMapper.class),
                eq(1L),
                eq(2L),
                eq("VACANT"),
                eq(10),
                eq(10)
        );
        assertTrue(sqlCaptor.getValue().contains("r.rent_status AS status"));
        assertTrue(sqlCaptor.getValue().contains("AS contractStage"));
        assertTrue(sqlCaptor.getValue().contains(
                "AND r.building_id = ? AND r.floor_id = ? AND r.rent_status = ?"
        ));
        assertTrue(sqlCaptor.getValue().contains("ORDER BY r.room_no ASC, r.id ASC LIMIT ? OFFSET ?"));
    }

    @Test
    void roomsApplyWhitelistedGroupAndSortFields() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(
                Map.of("id", 11L, "roomNumber", "1101")
        ));

        new BuildingController(jdbcTemplate).rooms(
                1L,
                null,
                null,
                "area:desc,unknown:asc,status:asc",
                "floorName:asc,status:desc",
                1,
                20
        );

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                sqlCaptor.capture(),
                any(RowMapper.class),
                eq(1L),
                eq(20),
                eq(0)
        );
        assertEquals(true, sqlCaptor.getValue().contains(
                "ORDER BY f.name ASC, r.rent_status DESC, r.area DESC, r.id DESC"
        ));
        assertEquals(false, sqlCaptor.getValue().contains("unknown"));
    }

    @Test
    void roomsExposeContractStageWithoutChangingOccupancyStatus() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(
                Map.of("id", 11L, "roomNumber", "1101", "status", "VACANT", "contractStage", "DRAFT")
        ));

        new BuildingController(jdbcTemplate).rooms(1L, 2L, null, 1, 20);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                sqlCaptor.capture(),
                any(RowMapper.class),
                eq(1L),
                eq(2L),
                eq(20),
                eq(0)
        );
        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("r.rent_status AS status"));
        assertTrue(sql.contains("AS contractStage"));
        assertTrue(sql.contains("contract_link.status = 'DRAFT'"));
        assertTrue(sql.contains("'PENDING_APPROVAL'"));
        assertTrue(sql.contains("contract_link.status = 'ACTIVE'"));
        assertTrue(sql.contains("c.room_id IS NOT NULL"));
        assertTrue(sql.contains("cr.id IS NULL"));
    }

    @Test
    void legacyBuildingFloorRoomsRouteReturnsRoomList() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq(1L), eq(2L))).thenReturn(List.of(
                Map.of("id", 21L, "roomNumber", "2101")
        ));

        ApiResponse<List<Map<String, Object>>> response = new BuildingController(jdbcTemplate)
                .buildingFloorRooms(1L, 2L);

        assertEquals(200, response.code());
        assertEquals(1, response.data().size());
        assertEquals("2101", response.data().getFirst().get("roomNumber"));
        verify(jdbcTemplate).queryForList(anyString(), eq(1L), eq(2L));
    }

    @Test
    void floorsExposeTotalArea() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq(1L))).thenReturn(List.of(
                Map.of("id", 2L, "floorNumber", 1, "totalArea", new BigDecimal("123.45"))
        ));

        ApiResponse<List<Map<String, Object>>> response = new BuildingController(jdbcTemplate).floors(1L);

        assertEquals(200, response.code());
        assertEquals(new BigDecimal("123.45"), response.data().getFirst().get("totalArea"));
        verify(jdbcTemplate).queryForList(
                eq("""
                SELECT id,
                       code AS floorCode,
                       building_id AS buildingId,
                       floor_no AS floorNumber,
                       name AS floorName,
                       total_area AS totalArea,
                       status,
                       remark AS description
                FROM building_floor
                WHERE deleted = 0
                 AND building_id = ? ORDER BY floor_no ASC, id ASC"""),
                eq(1L)
        );
    }

    @Test
    void createAndUpdateFloorPersistTotalArea() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        BuildingController controller = new BuildingController(jdbcTemplate);
        controller.createFloor(Map.of("buildingId", 1L, "floorNumber", 3, "totalArea", "456.78"));
        controller.updateFloor(9L, Map.of("buildingId", 1L, "floorNumber", 3, "totalArea", "789.01"));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).update(sqlCaptor.capture(), argsCaptor.capture());

        assertEquals(true, sqlCaptor.getAllValues().stream().anyMatch(sql -> sql.contains("total_area")));
        assertEquals(true, argsCaptor.getAllValues().stream()
                .anyMatch(args -> Arrays.asList(args).contains(new BigDecimal("456.78"))));
        assertEquals(true, argsCaptor.getAllValues().stream()
                .anyMatch(args -> Arrays.asList(args).contains(new BigDecimal("789.01"))));
    }
}
